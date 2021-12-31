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
package org.apache.knox.gateway.websockets;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.webshell.WebshellWebSocketAdapter;
import org.apache.knox.gateway.webshell.WebshellWebSocketAdapterFactory;
import org.easymock.EasyMock;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.easymock.EasyMock.isA;


public class GatewayWebsocketHandlerTest {

    @Test
    public void testValidWebshellRequest() throws Exception{
        GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.isWebShellEnabled()).andReturn(true).anyTimes();
        EasyMock.expect(gatewayConfig.getMaximumConcurrentWebshells()).andReturn(3).anyTimes();

        GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        ServletUpgradeRequest req = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        URI requestURI = new URI("wss://localhost:8443/gateway/webshell");
        EasyMock.expect(req.getRequestURI()).andReturn(requestURI).anyTimes();
        ServletUpgradeResponse resp = EasyMock.createNiceMock(ServletUpgradeResponse.class);

        JWTValidatorFactory jwtValidatorFactory = EasyMock.createNiceMock(JWTValidatorFactory.class);
        WebshellWebSocketAdapterFactory webshellWebSocketAdapterFactory = EasyMock.createNiceMock(WebshellWebSocketAdapterFactory.class);
        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.validate()).andReturn(true).anyTimes();
        EasyMock.expect(jwtValidatorFactory.create(req, gatewayServices, gatewayConfig)).andReturn(jwtValidator).anyTimes();

        WebshellWebSocketAdapter webshellWebSocketAdapter = EasyMock.createNiceMock(WebshellWebSocketAdapter.class);
        EasyMock.expect(webshellWebSocketAdapterFactory.create(isA(ExecutorService.class), isA(GatewayConfig.class), isA(JWTValidator.class), isA(AtomicInteger.class))).andReturn(webshellWebSocketAdapter).anyTimes();
        EasyMock.replay(req,resp,gatewayServices,gatewayConfig,jwtValidator,jwtValidatorFactory,webshellWebSocketAdapter,webshellWebSocketAdapterFactory);

        GatewayWebsocketHandler gatewayWebsocketHandler = new GatewayWebsocketHandler(gatewayConfig,gatewayServices, webshellWebSocketAdapterFactory,jwtValidatorFactory);
        Assert.assertTrue(gatewayWebsocketHandler.createWebSocket(req,resp) instanceof WebshellWebSocketAdapter);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testWebshellRequestWithInvalidJWT() throws Exception{
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("No valid token found for Web Shell connection");
        GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.isWebShellEnabled()).andReturn(true).anyTimes();
        EasyMock.expect(gatewayConfig.getMaximumConcurrentWebshells()).andReturn(3).anyTimes();

        GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        ServletUpgradeRequest req = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        URI requestURI = new URI("wss://localhost:8443/gateway/webshell");
        EasyMock.expect(req.getRequestURI()).andReturn(requestURI).anyTimes();
        ServletUpgradeResponse resp = EasyMock.createNiceMock(ServletUpgradeResponse.class);

        JWTValidatorFactory jwtValidatorFactory = EasyMock.createNiceMock(JWTValidatorFactory.class);
        WebshellWebSocketAdapterFactory webshellWebSocketAdapterFactory = EasyMock.createNiceMock(WebshellWebSocketAdapterFactory.class);
        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.validate()).andReturn(false).anyTimes();
        EasyMock.expect(jwtValidatorFactory.create(req, gatewayServices, gatewayConfig)).andReturn(jwtValidator).anyTimes();

        WebshellWebSocketAdapter webshellWebSocketAdapter = EasyMock.createNiceMock(WebshellWebSocketAdapter.class);
        EasyMock.expect(webshellWebSocketAdapterFactory.create(isA(ExecutorService.class), isA(GatewayConfig.class), isA(JWTValidator.class), isA(AtomicInteger.class))).andReturn(webshellWebSocketAdapter).anyTimes();
        EasyMock.replay(req,resp,gatewayServices,gatewayConfig,jwtValidator,jwtValidatorFactory,webshellWebSocketAdapter,webshellWebSocketAdapterFactory);

        GatewayWebsocketHandler gatewayWebsocketHandler = new GatewayWebsocketHandler(gatewayConfig,gatewayServices, webshellWebSocketAdapterFactory,jwtValidatorFactory);
        gatewayWebsocketHandler.createWebSocket(req,resp);
    }


    @Test
    public void testDisabledWebShell() throws Exception{
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Web Shell not enabled");
        GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.isWebShellEnabled()).andReturn(false).anyTimes();

        GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        ServletUpgradeRequest req = EasyMock.createNiceMock(ServletUpgradeRequest.class);
        URI requestURI = new URI("wss://localhost:8443/gateway/webshell");
        EasyMock.expect(req.getRequestURI()).andReturn(requestURI).anyTimes();
        ServletUpgradeResponse resp = EasyMock.createNiceMock(ServletUpgradeResponse.class);

        JWTValidatorFactory jwtValidatorFactory = EasyMock.createNiceMock(JWTValidatorFactory.class);
        WebshellWebSocketAdapterFactory webshellWebSocketAdapterFactory = EasyMock.createNiceMock(WebshellWebSocketAdapterFactory.class);
        EasyMock.replay(req,resp,gatewayServices,gatewayConfig,jwtValidatorFactory,webshellWebSocketAdapterFactory);

        GatewayWebsocketHandler gatewayWebsocketHandler = new GatewayWebsocketHandler(gatewayConfig,gatewayServices, webshellWebSocketAdapterFactory,jwtValidatorFactory);
        gatewayWebsocketHandler.createWebSocket(req,resp);
    }
}
