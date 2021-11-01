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
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.Tokens;
import org.apache.knox.gateway.websockets.ProxyWebSocketAdapter;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;

public class WebshellWebSocketAdapter extends ProxyWebSocketAdapter {
    private Session session;
    private ConnectionInfo connectionInfo;
    private final String username;
    private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
    private static final String JWT_DEFAULT_ISSUER = "KNOXSSO";

    private String cookieName;
    private String expectedIssuer;

    public WebshellWebSocketAdapter(ServletUpgradeRequest req,  ExecutorService pool, GatewayConfig config) {
        super(null, pool, null, config);
        username = req.getRequestURI().getRawQuery();
        try {
            checkCookieForValidation(req);
        } catch (final UnknownTokenException e){
            LOG.onError("no valid token found");
            throw new RuntimeException(e);
        }
        this.connectionInfo = new ProcessConnectionInfo(username);
    }

    private boolean validateToken(ServletUpgradeRequest req, JWT token) throws UnknownTokenException {
        // todo: this is a stub, implement this referencing AbstractJWTFilter validateToken function
        final String tokenId = TokenUtils.getTokenId(token);
        final String displayableTokenId = Tokens.getTokenIDDisplayText(tokenId);
        final String displayableToken = Tokens.getTokenDisplayText(token.toString());
        // todo: expectedIssuer can be configurable
        expectedIssuer = JWT_DEFAULT_ISSUER;
        LOG.debugLog(displayableToken);
        return true;
    }

    private void checkCookieForValidation(ServletUpgradeRequest req) throws UnknownTokenException {
        // todo: cookieName can be configurable
        cookieName = DEFAULT_SSO_COOKIE_NAME;
        List<HttpCookie> ssoCookies = req.getCookies();
        for (HttpCookie ssoCookie : ssoCookies) {
            if (!cookieName.equals(ssoCookie.getName())) {
                continue;
            }
            try {
                JWT token = new JWTToken(ssoCookie.getValue());
                if (validateToken(req, token)) {
                    // we found a valid cookie we don't need to keep checking anymore
                    return;
                }
            } catch (ParseException | UnknownTokenException ignore) {
                // Ignore the error since cookie was invalid
                // Fall through to keep checking if there are more cookies
            }
            throw new UnknownTokenException("No valid cookie found for webshell connection");
        }
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        this.session = session;
        LOG.debugLog("websocket connected.");
        connectionInfo.connect();
        pool.execute(new Runnable() {
            @Override
            public void run(){
                blockingReadFromHost();
            }
        });
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
            LOG.onError("Error reading from host:" + e.getMessage());
            throw new RuntimeIOException(e);
        }
    }

    @SuppressWarnings("PMD.DoNotUseThreads")
    @Override
    public void onWebSocketText(final String message) {
        LOG.debugLog("received message "+ message);

        ObjectMapper objectMapper = new ObjectMapper();
        final WebShellData webShellData;
        try {
            webShellData = objectMapper.readValue(message, WebShellData.class);
        } catch (JsonProcessingException e) {
            LOG.onError("error parsing string to WebShellData");
            throw new RuntimeException(e);
        }

        // todo: throw exception, or add extra validation using session id
        if (!connectionInfo.getUsername().equals(webShellData.getUsername())){
            throw new RuntimeException("Unauthorized user");
        }
        transToHost(webShellData.getCommand());
    }



    private void transToHost (String command){
        LOG.debugLog("sending to host: " + command);
        try {
            connectionInfo.getOutputStream().write(command.getBytes(StandardCharsets.UTF_8));
            connectionInfo.getOutputStream().flush();
        }catch (IOException e){
            LOG.onError("Error sending message to host");
            throw new RuntimeIOException(e);
        }
    }

    private void transToClient(String message){
        LOG.debugLog("sending to client: "+ message);
        try {
            session.getRemote().sendString(message);
        }catch (IOException e){
            LOG.onError("Error sending message to client");
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
        LOG.onError(t.toString());
        cleanup();
    }

    private void cleanup() {
        if(session != null && !session.isOpen()) {
            session.close();
        }
        if (connectionInfo != null) connectionInfo.disconnect();
    }
}
