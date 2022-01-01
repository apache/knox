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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
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
import org.eclipse.jetty.websocket.api.Session;

public class WebshellWebSocketAdapter extends ProxyWebSocketAdapter  {
    private Session session;
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
    public void onWebSocketConnect(final Session session) {
        this.session = session;
        connectionInfo.connect();
        pool.execute(this::blockingReadFromHost);
    }

    private void blockingReadFromHost(){
        byte[] buffer = new byte[config.getWebShellReadBufferSize()];
        int bytesRead;
        try {
            while ((bytesRead = connectionInfo.getInputStream().read(buffer)) != -1) {
                transToClient(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
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

    private void transToClient(String message){
        try {
            session.getRemote().sendString(message);
        } catch (IOException e){
            LOG.onError("Error sending message to client");
            cleanup();
        }
    }

    @Override
    public void onWebSocketBinary(final byte[] payload, final int offset, final int length) {
        throw new UnsupportedOperationException(
                "Websocket for binary messages is not supported at this time.");
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
        if(session != null && session.isOpen()) {
            session.close();
            session = null;
        }
        connectionInfo.disconnect();
    }
}
