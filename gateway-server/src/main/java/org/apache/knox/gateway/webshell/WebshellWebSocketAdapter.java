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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.websockets.JWTValidator;
import org.apache.knox.gateway.websockets.ProxyWebSocketAdapter;
import org.eclipse.jetty.websocket.api.Session;

public class WebshellWebSocketAdapter extends ProxyWebSocketAdapter  {
    private Session session;
    private ConnectionInfo connectionInfo;
    private JWTValidator jwtValidator;
    private StringBuilder messageBuffer;

    public WebshellWebSocketAdapter(ExecutorService pool, GatewayConfig config, JWTValidator jwtValidator) {
        super(null, pool, null, config);
        this.jwtValidator = jwtValidator;
        messageBuffer = new StringBuilder();
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        this.session = session;
        if (jwtValidator.getUsername() == null){
            throw new RuntimeException("Needs user name in JWT to use WebShell");
        }
        connectionInfo = new ConnectionInfo(jwtValidator.getUsername(), LOG);
        connectionInfo.connect();
        pool.execute(new Runnable() {
            @Override
            public void run() {
                blockingReadFromHost();
            }
        });
    }

    // this function will block, should be run in an asynchronous thread
    private void blockingReadFromHost(){
        byte[] buffer = new byte[1024];
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

    @SuppressWarnings("PMD.DoNotUseThreads")
    @Override
    public void onWebSocketText(final String message) {
        try {
            if (jwtValidator.tokenIsStillValid()) {
                ObjectMapper objectMapper = new ObjectMapper();
                WebshellData webshellData = objectMapper.readValue(message, WebshellData.class);
                transToHost(webshellData.getCommand());
            } else {
                throw new RuntimeException("Token expired");
            }
        } catch (JsonProcessingException | UnknownTokenException | RuntimeException e){
            LOG.onError(e.toString());
            cleanup();
        }
    }
    private void logCommand(){
        LOG.debugLog(String.format("[User %s to bash process %d --->] %s",
                connectionInfo.getUsername(),
                connectionInfo.getPid(),
                messageBuffer.toString()));
        messageBuffer.setLength(0);
    }

    private void transToHost (String command){
        messageBuffer.append(command);
        // todo: is this way to detect new line platform independent?
        String CarriageReturn = Character.toString((char)13);
        if (command.contains(CarriageReturn)){
            logCommand();
        }
        try {
            connectionInfo.getOutputStream().write(command.getBytes(StandardCharsets.UTF_8));
            connectionInfo.getOutputStream().flush();
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
        super.onWebSocketClose(statusCode, reason);
        cleanup();
    }

    @Override
    public void onWebSocketError(final Throwable t) {
        LOG.onError(t.toString());
        cleanup();
    }

    private void cleanup() {
        logCommand();
        if(session != null && !session.isOpen()) {
            session.close();
        }
        if (connectionInfo != null) connectionInfo.disconnect();
    }
}
