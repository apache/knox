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

import static org.easymock.EasyMock.isA;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.websockets.JWTValidator;
import org.apache.knox.gateway.websockets.WebsocketLogMessages;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(PowerMockRunner.class)
@PrepareForTest({WebshellWebSocketAdapter.class, MessagesFactory.class, AuditServiceFactory.class})
public class WebshellWebsocketAdapterTest extends EasyMockSupport {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private GatewayConfig setupGatewayConfig(){
        GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(gatewayConfig.getGatewayPIDDir()).andReturn("testPIDDir").anyTimes();
        EasyMock.expect(gatewayConfig.getWebShellReadBufferSize()).andReturn(1024).anyTimes();
        EasyMock.expect(gatewayConfig.isWebShellAuditLoggingEnabled()).andReturn(false).anyTimes();
        EasyMock.replay(gatewayConfig);
        return gatewayConfig;
    }
    private void setupMessagesFactory(){
        PowerMock.mockStatic(MessagesFactory.class);
        EasyMock.expect(MessagesFactory.get(JWTMessages.class)).andReturn(EasyMock.createNiceMock(JWTMessages.class)).anyTimes();
        EasyMock.expect(MessagesFactory.get(WebsocketLogMessages.class)).andReturn(EasyMock.createNiceMock(WebsocketLogMessages.class)).anyTimes();
        PowerMock.replay(MessagesFactory.class);
    }
    private void setupAuditor(){
        PowerMock.mockStatic(AuditServiceFactory.class);
        AuditService auditService = EasyMock.createNiceMock(AuditService.class);
        EasyMock.expect(AuditServiceFactory.getAuditService()).andReturn(auditService).anyTimes();
        Auditor auditor = EasyMock.createNiceMock(Auditor.class);
        EasyMock.expect(auditService.getAuditor(isA(String.class),isA(String.class),isA(String.class))).andReturn(auditor).anyTimes();
        PowerMock.replay(AuditServiceFactory.class);
        EasyMock.replay(auditService,auditor);
    }

    @Test
    public void testOnWebSocketConnect() throws Exception{

        GatewayConfig gatewayConfig = setupGatewayConfig();
        setupMessagesFactory();
        setupAuditor();

        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.getUsername()).andReturn("Alice").anyTimes();
        EasyMock.replay(jwtValidator);

        ConnectionInfo connectionInfo = PowerMock.createMock(ConnectionInfo.class);
        PowerMock.expectNew(ConnectionInfo.class, isA(String.class) , isA(String.class), isA(AtomicInteger.class)).andReturn(connectionInfo);
        connectionInfo.connect();

        String bashOuput = "fake bash output";
        InputStream inputStream = new ByteArrayInputStream(bashOuput.getBytes(StandardCharsets.UTF_8));
        EasyMock.expect(connectionInfo.getInputStream()).andReturn(inputStream).times(1);
        InputStream inputStreamEmpty = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        EasyMock.expect(connectionInfo.getInputStream()).andReturn(inputStreamEmpty).times(1);

        Session session = EasyMock.createNiceMock(Session.class);
        RemoteEndpoint remote = EasyMock.createNiceMock(RemoteEndpoint.class);
        // send to client
        EasyMock.expect(session.getRemote()).andReturn(remote).anyTimes();
        remote.sendString(bashOuput);
        // clean up
        EasyMock.expect(session.isOpen()).andReturn(true).anyTimes();
        session.close();
        EasyMock.replay(session, remote);

        connectionInfo.disconnect();
        PowerMock.replay(connectionInfo, ConnectionInfo.class);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger concurrentWebshells = new AtomicInteger(0);
        WebshellWebSocketAdapter webshellWebSocketAdapter = new WebshellWebSocketAdapter(pool, gatewayConfig, jwtValidator, concurrentWebshells);
        webshellWebSocketAdapter.onWebSocketConnect(session);
        verifyAll();
    }

    @Test
    public void testOnWebSocketConnectWithoutUsername() throws Exception {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Needs user name in JWT to use WebShell");
        GatewayConfig gatewayConfig = setupGatewayConfig();
        setupMessagesFactory();
        setupAuditor();

        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.getUsername()).andReturn(null).anyTimes();
        EasyMock.replay(jwtValidator);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger concurrentWebshells = new AtomicInteger(0);
        new WebshellWebSocketAdapter(pool, gatewayConfig, jwtValidator, concurrentWebshells);
        verifyAll();
    }


    @Test
    public void testOnWebSocketText() throws Exception{

        GatewayConfig gatewayConfig = setupGatewayConfig();
        setupMessagesFactory();
        setupAuditor();

        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.getUsername()).andReturn("Alice").anyTimes();
        EasyMock.expect(jwtValidator.tokenIsStillValid()).andReturn(true).anyTimes();
        EasyMock.replay(jwtValidator);

        ConnectionInfo connectionInfo = PowerMock.createMock(ConnectionInfo.class);
        PowerMock.expectNew(ConnectionInfo.class, isA(String.class) , isA(String.class), isA(AtomicInteger.class)).andReturn(connectionInfo);
        connectionInfo.connect();

        OutputStream outputStream = EasyMock.createNiceMock(OutputStream.class);
        EasyMock.expect(connectionInfo.getOutputStream()).andReturn(outputStream).times(2);
        outputStream.write("fake user input".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        PowerMock.replay(connectionInfo, ConnectionInfo.class);
        EasyMock.replay(outputStream);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger concurrentWebshells = new AtomicInteger(0);
        WebshellWebSocketAdapter webshellWebSocketAdapter = new WebshellWebSocketAdapter(pool, gatewayConfig, jwtValidator, concurrentWebshells);
        webshellWebSocketAdapter.onWebSocketText("{ \"userInput\" : \"fake user input\"}");
        verifyAll();
    }

    @Test
    public void testOnWebSocketTextWithExpiredToken() throws Exception {
        GatewayConfig gatewayConfig = setupGatewayConfig();
        setupMessagesFactory();
        setupAuditor();

        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.getUsername()).andReturn("Alice").anyTimes();
        EasyMock.expect(jwtValidator.tokenIsStillValid()).andReturn(false).anyTimes();
        EasyMock.replay(jwtValidator);

        ConnectionInfo connectionInfo = PowerMock.createMock(ConnectionInfo.class);
        PowerMock.expectNew(ConnectionInfo.class, isA(String.class) , isA(String.class), isA(AtomicInteger.class)).andReturn(connectionInfo);
        connectionInfo.disconnect();
        PowerMock.replay(connectionInfo, ConnectionInfo.class);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger concurrentWebshells = new AtomicInteger(0);
        WebshellWebSocketAdapter webshellWebSocketAdapter = new WebshellWebSocketAdapter(pool, gatewayConfig, jwtValidator, concurrentWebshells);
        webshellWebSocketAdapter.onWebSocketText("{ \"userInput\" : \"fake user input\"}");
        verifyAll();
    }

    @Test
    public void testOnWebSocketClose() throws Exception {
        GatewayConfig gatewayConfig = setupGatewayConfig();
        setupMessagesFactory();
        setupAuditor();

        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.getUsername()).andReturn("Alice").anyTimes();
        EasyMock.replay(jwtValidator);

        ConnectionInfo connectionInfo = PowerMock.createMock(ConnectionInfo.class);
        PowerMock.expectNew(ConnectionInfo.class, isA(String.class) , isA(String.class), isA(AtomicInteger.class)).andReturn(connectionInfo);
        connectionInfo.disconnect();
        PowerMock.replay(connectionInfo, ConnectionInfo.class);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger concurrentWebshells = new AtomicInteger(0);
        WebshellWebSocketAdapter webshellWebSocketAdapter = new WebshellWebSocketAdapter(pool, gatewayConfig, jwtValidator, concurrentWebshells);
        webshellWebSocketAdapter.onWebSocketClose(0,"reason");
        verifyAll();
    }

    @Test
    public void testOnWebSocketError() throws Exception{
        GatewayConfig gatewayConfig = setupGatewayConfig();
        setupMessagesFactory();
        setupAuditor();

        JWTValidator jwtValidator = EasyMock.createNiceMock(JWTValidator.class);
        EasyMock.expect(jwtValidator.getUsername()).andReturn("Alice").anyTimes();
        EasyMock.replay(jwtValidator);

        ConnectionInfo connectionInfo = PowerMock.createMock(ConnectionInfo.class);
        PowerMock.expectNew(ConnectionInfo.class, isA(String.class) , isA(String.class), isA(AtomicInteger.class)).andReturn(connectionInfo);
        connectionInfo.disconnect();
        PowerMock.replay(connectionInfo, ConnectionInfo.class);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger concurrentWebshells = new AtomicInteger(0);
        WebshellWebSocketAdapter webshellWebSocketAdapter = new WebshellWebSocketAdapter(pool, gatewayConfig, jwtValidator, concurrentWebshells);
        webshellWebSocketAdapter.onWebSocketError(new Throwable());
        verifyAll();
    }
}
