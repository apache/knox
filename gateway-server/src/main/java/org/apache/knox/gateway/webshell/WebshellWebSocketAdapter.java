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
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebshellWebSocketAdapter extends WebSocketAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(WebshellWebSocketAdapter.class);

    private Session session;

    private final ExecutorService pool;

    private ConnectionInfo connectionInfo;

    public static final String OPERATION_CONNECT = "connect";
    public static final String OPERATION_COMMAND = "command";

    public WebshellWebSocketAdapter(ServletUpgradeRequest req, ExecutorService pool) {
        super();
        this.pool = pool;
        //todo: validate hadoop-jwt cookie
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        this.session = session;
        //todo: process based works on mac but not on Centos 7, use JSch to connect for now
        //this.connectionInfo = new ProcessConnectionInfo();
        this.connectionInfo = new JSchConnectionInfo();
        LOG.info("websocket connected.");
    }


    @SuppressWarnings("PMD.DoNotUseThreads")
    @Override
    public void onWebSocketText(final String message) {
        LOG.info("received message "+ message);

        ObjectMapper objectMapper = new ObjectMapper();
        final WebShellData webShellData;
        try {
            webShellData = objectMapper.readValue(message, WebShellData.class);
        } catch (JsonProcessingException e) {
            LOG.error("error parsing string to WebShellData");
            throw new RuntimeException(e);
        }

        if (OPERATION_CONNECT.equals(webShellData.getOperation())) {
            connectionInfo.connect(webShellData.getUsername());
            pool.execute(new Runnable() {
                @Override
                public void run(){
                    blockingReadFromHost();
                }
            });
        } else if (OPERATION_COMMAND.equals(webShellData.getOperation())) {
            transToHost(webShellData.getCommand());
        } else {
            String err = String.format(Locale.ROOT, "Operation %s not supported",webShellData.getOperation());
            LOG.error(err);
            throw new UnsupportedOperationException(err);
        }
    }

    // this function will block, should be run in an asynchronous thread
    private void blockingReadFromHost(){
        LOG.info("start listening to bash process");
        byte[] buffer = new byte[1024];
        int i;
        try {
            // blocks until data comes in
            while ((i = connectionInfo.getInputStream().read(buffer)) != -1) {
                transToClient(new String(buffer, 0, i, StandardCharsets.UTF_8));
            }
        } catch (IOException e){
            LOG.error("Error reading from host:" + e.getMessage());
            throw new RuntimeIOException(e);
        }
    }

    private void transToHost (String command){
        LOG.info("sending to host: "+command);
        try {
            connectionInfo.getOutputStream().write(command.getBytes(StandardCharsets.UTF_8));
            connectionInfo.getOutputStream().flush();
        }catch (IOException e){
            LOG.error("Error sending message to host");
            throw new RuntimeIOException(e);
        }
    }

    private void transToClient(String message){
        LOG.info("sending to client: "+ message);
        try {
            session.getRemote().sendString(message);
        }catch (IOException e){
            LOG.error("Error sending message to client");
            throw new RuntimeIOException(e);
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
        cleanupOnError(t);
    }

    /**
     * Cleanup sessions
     */
    private void cleanupOnError(final Throwable t) {
        LOG.error(t.toString());
        cleanup();
    }

    private void cleanup() {
        if(session != null && !session.isOpen()) {
            session.close();
        }
        if (connectionInfo != null) connectionInfo.disconnect();
    }
}
