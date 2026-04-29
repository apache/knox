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

import static org.easymock.EasyMock.isA;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.webshell.WebshellWebSocketAdapter;
import org.easymock.EasyMock;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.api.util.WSURI;
import org.eclipse.jetty.websocket.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.ServerUpgradeResponse;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
@PrepareForTest({JWTValidatorFactory.class, MessagesFactory.class, GatewayWebsocketHandler.class, AuditServiceFactory.class})
public class GatewayWebsocketHandlerTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // setup MessageFactory for static variable
        PowerMock.mockStatic(MessagesFactory.class);
        EasyMock.expect(MessagesFactory.get(GatewaySpiMessages.class)).andReturn(EasyMock.createNiceMock(GatewaySpiMessages.class)).anyTimes();
        EasyMock.expect(MessagesFactory.get(JWTMessages.class)).andReturn(EasyMock.createNiceMock(JWTMessages.class)).anyTimes();
        EasyMock.expect(MessagesFactory.get(WebsocketLogMessages.class)).andReturn(EasyMock.createNiceMock(WebsocketLogMessages.class)).anyTimes();
        PowerMock.replay(MessagesFactory.class);
        // setup Auditor for static variable
        PowerMock.mockStatic(AuditServiceFactory.class);
        AuditService auditService = EasyMock.createNiceMock(AuditService.class);
        EasyMock.expect(AuditServiceFactory.getAuditService()).andReturn(auditService).anyTimes();
        Auditor auditor = EasyMock.createNiceMock(Auditor.class);
        EasyMock.expect(auditService.getAuditor(isA(String.class),isA(String.class),isA(String.class))).andReturn(auditor).anyTimes();
        PowerMock.replay(AuditServiceFactory.class);
        EasyMock.replay(auditService,auditor);
    }

    @Test
    public void testValidWebShellRequest() throws Exception{
        // mock GatewayConfig and GatewayServices
        GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.isWebShellEnabled()).andReturn(true).anyTimes();
        EasyMock.expect(gatewayConfig.getMaximumConcurrentWebshells()).andReturn(3).anyTimes();
        GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        // mock ServerUpgradeRequest and ServerUpgradeResponse
        ServerUpgradeRequest req = createServerUpgradeRequest("wss://localhost:8443/gateway/webshell");
        ServerUpgradeResponse resp = createServerUpgradeResponse();
        Callback callback = EasyMock.createNiceMock(Callback.class);
        EasyMock.replay(callback);

        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.validate()).andReturn(true).anyTimes();
        EasyMock.expect(jwtValidator.getUsername()).andReturn("testUser").anyTimes();
        PowerMock.mockStatic(JWTValidatorFactory.class);
        EasyMock.expect(JWTValidatorFactory.create(req, gatewayServices, gatewayConfig)).andReturn(jwtValidator).anyTimes();

        WebshellWebSocketAdapter webshellWebSocketAdapter = PowerMock.createMock(WebshellWebSocketAdapter.class);
        PowerMock.expectNew(WebshellWebSocketAdapter.class,isA(ExecutorService.class) , isA(GatewayConfig.class), isA(JWTValidator.class), isA(AtomicInteger.class)).andReturn(webshellWebSocketAdapter);
        EasyMock.replay(gatewayServices,gatewayConfig,jwtValidator);
        PowerMock.replayAll();

        KnoxWebSocketCreator knoxWebSocketCreator = new KnoxWebSocketCreator(gatewayConfig,gatewayServices);

        Assert.assertTrue(knoxWebSocketCreator.createWebSocket(req,resp,callback) instanceof WebshellWebSocketAdapter);
    }

    @Test
    public void testValidWebShellRequestThroughLB() throws Exception{
      // mock GatewayConfig and GatewayServices
      GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
      EasyMock.expect(gatewayConfig.isWebShellEnabled()).andReturn(true).anyTimes();
      EasyMock.expect(gatewayConfig.getMaximumConcurrentWebshells()).andReturn(3).anyTimes();
      GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
      // mock ServerUpgradeRequest and ServerUpgradeResponse
      ServerUpgradeRequest req = createServerUpgradeRequest("wss://www.local.com/gateway/webshell");
      ServerUpgradeResponse resp = createServerUpgradeResponse();
      Callback callback = EasyMock.createNiceMock(Callback.class);
      EasyMock.replay(callback);
      JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
      EasyMock.expect(jwtValidator.validate()).andReturn(true).anyTimes();
      EasyMock.expect(jwtValidator.getUsername()).andReturn("testUser").anyTimes();
      PowerMock.mockStatic(JWTValidatorFactory.class);
      EasyMock.expect(JWTValidatorFactory.create(req, gatewayServices, gatewayConfig)).andReturn(jwtValidator).anyTimes();

      WebshellWebSocketAdapter webshellWebSocketAdapter = PowerMock.createMock(WebshellWebSocketAdapter.class);
      PowerMock.expectNew(WebshellWebSocketAdapter.class,isA(ExecutorService.class) , isA(GatewayConfig.class), isA(JWTValidator.class), isA(AtomicInteger.class)).andReturn(webshellWebSocketAdapter);
      EasyMock.replay(gatewayServices,gatewayConfig,jwtValidator);
      PowerMock.replayAll();

      KnoxWebSocketCreator knoxWebSocketCreator = new KnoxWebSocketCreator(gatewayConfig,gatewayServices);
      Assert.assertTrue(knoxWebSocketCreator.createWebSocket(req,resp, callback) instanceof WebshellWebSocketAdapter);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testWebShellRequestWithInvalidJWT() throws Exception{
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("No valid token found for Web Shell connection");
        // mock GatewayConfig and GatewayServices
        GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.isWebShellEnabled()).andReturn(true).anyTimes();
        EasyMock.expect(gatewayConfig.getMaximumConcurrentWebshells()).andReturn(3).anyTimes();
        GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        // mock ServerUpgradeRequest and ServerUpgradeResponse
        ServerUpgradeRequest req = createServerUpgradeRequest("wss://localhost:8443/gateway/webshell");
        ServerUpgradeResponse resp = createServerUpgradeResponse();
        Callback callback = EasyMock.createNiceMock(Callback.class);
        EasyMock.replay(callback);

        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.validate()).andReturn(false).anyTimes();
        PowerMock.mockStatic(JWTValidatorFactory.class);
        EasyMock.expect(JWTValidatorFactory.create(req, gatewayServices, gatewayConfig)).andReturn(jwtValidator).anyTimes();

        EasyMock.replay(gatewayServices,gatewayConfig,jwtValidator);
        PowerMock.replayAll();

        KnoxWebSocketCreator knoxWebSocketCreator = new KnoxWebSocketCreator(gatewayConfig,gatewayServices);
        knoxWebSocketCreator.createWebSocket(req,resp,callback);
    }


    @Test
    public void testDisabledWebShell() throws Exception{
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Web Shell not enabled");
        // mock GatewayConfig and GatewayServices
        GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.isWebShellEnabled()).andReturn(false).anyTimes();
        GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
        // mock ServerUpgradeRequest and ServerUpgradeResponse
        ServerUpgradeRequest req = createServerUpgradeRequest("wss://localhost:8443/gateway/webshell");
        ServerUpgradeResponse resp = createServerUpgradeResponse();
        Callback callback = EasyMock.createNiceMock(Callback.class);
        EasyMock.replay(callback);
        EasyMock.replay(gatewayServices,gatewayConfig);
        KnoxWebSocketCreator knoxWebSocketCreator = new KnoxWebSocketCreator(gatewayConfig,gatewayServices);
        knoxWebSocketCreator.createWebSocket(req,resp,callback);

    }

    private ServerUpgradeRequest createServerUpgradeRequest(String url) throws Exception {
        ServerUpgradeRequest mockRequest = EasyMock.createNiceMock(ServerUpgradeRequest.class);
        URI httpUri = WSURI.toHttp(new URI(url));

        HttpURI httpURI = HttpURI.build(httpUri);

        // Set the expectations needed by KnoxWebSocketCreator and JWTValidator
        EasyMock.expect(mockRequest.getHttpURI()).andReturn(httpURI).anyTimes();
        EasyMock.expect(mockRequest.getHeaders()).andReturn(HttpFields.EMPTY).anyTimes();

        EasyMock.replay(mockRequest);
        return mockRequest;
    }

    private ServerUpgradeResponse createServerUpgradeResponse() {
        ServerUpgradeResponse mockResponse = EasyMock.createNiceMock(ServerUpgradeResponse.class);

        EasyMock.replay(mockResponse);
        return mockResponse;
    }
}
