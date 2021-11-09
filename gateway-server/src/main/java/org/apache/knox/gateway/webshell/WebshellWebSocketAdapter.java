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
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.websockets.ProxyWebSocketAdapter;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;

public class WebshellWebSocketAdapter extends ProxyWebSocketAdapter  {
    private Session session;
    private ConnectionInfo connectionInfo;
    private WebShellTokenValidator tokenValidator;

    public WebshellWebSocketAdapter(ServletUpgradeRequest req,  ExecutorService pool, GatewayConfig config, GatewayServices services) {
        super(null, pool, null, config);
        this.tokenValidator = new WebShellTokenValidator(req, services, config);
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        if (tokenValidator.validateToken()){
            this.session = session;
            LOG.debugLog("websocket connected.");
            connectionInfo = new ProcessConnectionInfo(tokenValidator.getUsername());
            connectionInfo.connect();
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    blockingReadFromHost();
                }
            });
        } else{
            throw new RuntimeException("no valid token found for webshell access");
        }
    }

    // this function will block, should be run in an asynchronous thread
    private void blockingReadFromHost(){
        LOG.debugLog("start listening to bash process");
        byte[] buffer = new byte[1024];
        int bytesRead;
        try {
            // todo: maybe change to non-blocking read using java.nio
            while ((bytesRead = connectionInfo.getInputStream().read(buffer)) != -1) {
                transToClient(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
        } catch (IOException e){
            transToClient("bash process terminated unexpectedly");
        } finally {
            cleanupOnError("bash process terminated unexpectedly");
        }
    }

    @SuppressWarnings("PMD.DoNotUseThreads")
    @Override
    public void onWebSocketText(final String message) {
        //todo: check token expiry
        LOG.debugLog("received message "+ message);

        ObjectMapper objectMapper = new ObjectMapper();
        final WebShellData webShellData;
        try {
            webShellData = objectMapper.readValue(message, WebShellData.class);
            if (connectionInfo.getUsername().equals(webShellData.getUsername())){
                transToHost(webShellData.getCommand());
            } else {
                transToClient("received command from unrecognized user");
                throw new RuntimeException("Unrecognized user");
            }
        } catch (JsonProcessingException | RuntimeException e) {
            cleanupOnError(e.toString());
        }
    }



    private void transToHost (String command){
        LOG.debugLog("sending to host: " + command);
        try {
            connectionInfo.getOutputStream().write(command.getBytes(StandardCharsets.UTF_8));
            connectionInfo.getOutputStream().flush();
        } catch (IOException e){
            transToClient("Error sending message to host");
            cleanupOnError("Error sending message to host");
        }
    }

    private void transToClient(String message){
        LOG.debugLog("sending to client: "+ message);
        try {
            session.getRemote().sendString(message);
        } catch (IOException e){
            cleanupOnError("Error sending message to client");
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
        cleanupOnError(t.toString());
    }

    /**
     * Cleanup sessions
     */
    private void cleanupOnError(String e) {
        LOG.onError(e);
        cleanup();
    }

    private void cleanup() {
        if(session != null && !session.isOpen()) {
            session.close();
        }
        if (connectionInfo != null) connectionInfo.disconnect();
    }
}
