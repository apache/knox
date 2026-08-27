/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.webshell;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.websockets.JWTValidator;
import org.apache.knox.gateway.websockets.ProxyWebSocketAdapter;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;

public class WebshellWebSocketAdapter extends ProxyWebSocketAdapter  {
    // volatile: written in onWebSocketOpen and nulled in cleanup(), read from the
    // pump thread (blockingReadFromHost) and from Jetty I/O threads
    // (onWebSocketClose/Error). volatile gives those reads a consistent view.
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private volatile Session session;
    // Ensures the teardown in cleanup() runs exactly once no matter how many
    // threads reach it (pump-thread finally, close/error callbacks, text-handler
    // catch), so they can never race on the session field or double-close.
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ConnectionInfo connectionInfo;
    private final JWTValidator jwtValidator;
    private final StringBuilder auditBuffer; // buffer for audit log
    private final ObjectMapper objectMapper;
    private static final Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(
            AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
            AuditConstants.KNOX_COMPONENT_NAME );


    public WebshellWebSocketAdapter(ExecutorService pool, GatewayConfig config, JWTValidator jwtValidator, AtomicInteger concurrentWebshells) {
        super(null, pool, null, config);
        this.jwtValidator = jwtValidator;
        auditBuffer = new StringBuilder();
        if (jwtValidator.getUsername() == null){
            throw new RuntimeException("Needs user name in JWT to use WebShell");
        }
        connectionInfo = new ConnectionInfo(jwtValidator.getUsername(),config.getGatewayPIDDir(), concurrentWebshells);
        objectMapper = new ObjectMapper();
    }

    @SuppressWarnings("PMD.DoNotUseThreads")
    @Override
    public void onWebSocketOpen(final Session session) {
        this.session = session;
        connectionInfo.connect();
        pool.execute(this::blockingReadFromHost);
    }

    private void blockingReadFromHost(){
        byte[] buffer = new byte[config.getWebShellReadBufferSize()];
        int bytesRead;
        try {
            while ((bytesRead = connectionInfo.getInputStream().read(buffer)) != -1) {
                // Send this chunk and block until its send completes before
                // reading the next one. This is demand management applied to the
                // pty source: the next read (our "demand" for more shell output)
                // only happens after the previous send's callback has fired. That
                // guarantees at most one send is in flight at a time (Jetty 12
                // forbids overlapping sends on a session) and keeps every send
                // outcome on this single pump thread, so failures no longer race
                // cleanup() from a Jetty I/O thread.
                sendToClient(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
        } catch (IOException e){
            LOG.onError(e.toString());
        } finally {
            cleanup();
        }
    }

    @Override
    public void onWebSocketText(final String message) {
        try {
            if (jwtValidator.tokenIsStillValid()) {
                WebshellData webshellData = objectMapper.readValue(message, WebshellData.class);
                transToHost(webshellData.getUserInput());
            } else {
                throw new RuntimeException("Token expired");
            }
        } catch (JsonProcessingException | UnknownTokenException | RuntimeException e){
            LOG.onError(e.toString());
            cleanup();
        }
    }

    private void transToHost (String userInput){
        try {
            // forward userInput to bash process
            connectionInfo.getOutputStream().write(userInput.getBytes(StandardCharsets.UTF_8));
            connectionInfo.getOutputStream().flush();
            if (config.isWebShellAuditLoggingEnabled()) {
                audit(userInput);
            }
        } catch (IOException e){
            LOG.onError("Error sending message to host");
            cleanup();
        }
    }

    /**
     * Sends one chunk of shell output to the client and blocks the pump thread
     * until that send completes.
     *
     * <p>The send is asynchronous in Jetty 12 (it takes a {@link Callback}), but
     * we wait on its completion here so the caller cannot start the next send
     * until this one has finished — the Jetty 12 WebSocket contract allows only
     * one send outstanding at a time. On failure we throw rather than tearing the
     * session down inline: {@link #blockingReadFromHost} owns cleanup and runs it
     * once in its {@code finally}, which is what removes the old cross-thread
     * race on {@code session}/{@code cleanup()}.
     *
     * @throws IOException if the session is already closed, the send fails, or
     *         the pump thread is interrupted while waiting for the send
     */
    private void sendToClient(String message) throws IOException {
        final Session current = session;
        if (current == null || !current.isOpen()) {
            throw new IOException("Cannot send message to client; session is closed");
        }
        final CompletableFuture<Void> sent = new CompletableFuture<>();
        current.sendText(message, Callback.from(() -> sent.complete(null), sent::completeExceptionally));
        try {
            // Bounded in practice by Jetty's async-write / idle timeouts, which
            // fail the callback (completing this future exceptionally) if the peer
            // stops reading, so the pump thread cannot block here indefinitely.
            sent.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending message to client", e);
        } catch (ExecutionException e) {
            throw new IOException("Error sending message to client", e.getCause());
        }
    }

    @Override
    public void onWebSocketBinary(final ByteBuffer payload, final Callback callback) {
        // Binary is not supported for webshell sessions. Complete the callback so the
        // Jetty 12 demand loop can surface the error and close the connection cleanly
        // rather than throwing from the listener (which would leave demand stalled).
        callback.fail(new UnsupportedOperationException(
        "Websocket for binary messages is not supported at this time."));
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        LOG.debugLog("Closing websocket connection");
        cleanup();
    }

    @Override
    public void onWebSocketError(final Throwable t) {
        LOG.onError(t.toString());
        cleanup();
    }

    private String cleanText(String text){
        // remove scroll up and down control characters
        text = text.replaceAll("[\\^\\[OA|\\^\\[OB]", "");
        // remove control characters
        String noControl = CharMatcher.javaIsoControl().removeFrom(text);
        // remove invisible characters
        String printable = CharMatcher.invisible().removeFrom(noControl);
        // remove non-ascii characters
        String clean = CharMatcher.ascii().retainFrom(printable);
        return clean;
    }

    // todo: this is an approximate solution to audit commands sent to bash process
    // for more detailed discussion see design doc
    private void audit(String userInput){
        auditBuffer.append(userInput);
        if (userInput.contains("\r") || userInput.contains("\n")) {
            // we only log the part of the string before the first space
            String[] commands = auditBuffer.toString().trim().split("\\s+");
            if (commands.length > 0) {
                auditor.audit(Action.WEBSHELL, connectionInfo.getUsername() +
                                ':' + connectionInfo.getPid(), ResourceType.PROCESS,
                        ActionOutcome.SUCCESS, cleanText(commands[0]));
                auditBuffer.setLength(0);
            }
        }
    }

    private void cleanup() {
        // Idempotent + thread-safe. The pump thread's finally, the send-failure
        // path, onWebSocketClose/onWebSocketError (Jetty I/O threads) and the
        // onWebSocketText catch can all call this. compareAndSet lets only the
        // first caller perform the teardown; every later caller returns
        // immediately, so the session field is never raced on or closed twice.
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        final Session current = session;
        session = null;
        if (current != null && current.isOpen()) {
            current.close(StatusCode.NORMAL, null, Callback.NOOP);
        }
        connectionInfo.disconnect();
    }
}