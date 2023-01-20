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

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.websockets.WebsocketLogMessages;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.eclipse.jetty.io.RuntimeIOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.isA;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Runtime.class, MessagesFactory.class, AuditServiceFactory.class, ConnectionInfo.class})
public class ConnectionInfoTest extends EasyMockSupport {
    private static String testPIDdir = "webshell-test";
    private static String testUserName = "Alice";
    private static long testPID = 12345;
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static void setupMessagesFactory(){
        PowerMock.mockStatic(MessagesFactory.class);
        EasyMock.expect(MessagesFactory.get(WebsocketLogMessages.class)).andReturn(EasyMock.createNiceMock(WebsocketLogMessages.class)).anyTimes();
        PowerMock.replay(MessagesFactory.class);
    }
    private static void setupAuditService(){
        PowerMock.mockStatic(AuditServiceFactory.class);
        AuditService auditService = EasyMock.createNiceMock(AuditService.class);
        EasyMock.expect(AuditServiceFactory.getAuditService()).andReturn(auditService).anyTimes();
        Auditor auditor = EasyMock.createNiceMock(Auditor.class);
        EasyMock.expect(auditService.getAuditor(isA(String.class),isA(String.class),isA(String.class))).andReturn(auditor).anyTimes();
        PowerMock.replay(AuditServiceFactory.class);
        EasyMock.replay(auditService, auditor);
    }


    @SuppressWarnings("PMD.DoNotUseThreads")
    private static void setupRuntime(){
        PowerMock.mockStatic(Runtime.class);
        Runtime runtime = EasyMock.createMock(Runtime.class);
        runtime.addShutdownHook(isA(Thread.class));
        EasyMock.expect(runtime.removeShutdownHook(isA(Thread.class))).andReturn(true).anyTimes();
        EasyMock.replay(runtime);
        EasyMock.expect(Runtime.getRuntime()).andReturn(runtime).anyTimes();
        PowerMock.replay(Runtime.class);
    }

    @BeforeClass
    public static void setupBeforeClass(){
        setupMessagesFactory();
        setupAuditService();
        setupRuntime();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception{
        //remove testPIDdir directory
        FileUtils.deleteDirectory(new File(testPIDdir));

    }

    @Test
    public void testInstantiation() {
        new ConnectionInfo(testUserName, testPIDdir, new AtomicInteger(0));
        verifyAll();
    }

    @Test
    public void testConnectSuccess() throws Exception {
        AtomicInteger concurrentWebshell = EasyMock.createNiceMock(AtomicInteger.class);
        concurrentWebshell.incrementAndGet();
        concurrentWebshell.decrementAndGet();
        EasyMock.replay(concurrentWebshell);

        PtyProcess ptyProcess = EasyMock.createNiceMock(PtyProcess.class);
        EasyMock.expect(ptyProcess.pid()).andReturn(testPID);
        EasyMock.replay(ptyProcess);

        PtyProcessBuilder ptyProcessBuilder = PowerMock.createNiceMock(PtyProcessBuilder.class);
        String[] cmd = { "sudo","--user", testUserName ,"bash","-i"};
        EasyMock.expect(ptyProcessBuilder.setCommand(cmd)).andReturn(ptyProcessBuilder);
        EasyMock.expect(ptyProcessBuilder.setRedirectErrorStream(true)).andReturn(ptyProcessBuilder);
        EasyMock.expect(ptyProcessBuilder.setWindowsAnsiColorEnabled(true)).andReturn(ptyProcessBuilder);
        EasyMock.expect(ptyProcessBuilder.setInitialColumns(anyInt())).andReturn(ptyProcessBuilder);
        EasyMock.expect(ptyProcessBuilder.setInitialRows(anyInt())).andReturn(ptyProcessBuilder);
        EasyMock.expect(ptyProcessBuilder.setDirectory(anyString())).andReturn(ptyProcessBuilder);
        EasyMock.expect(ptyProcessBuilder.setEnvironment(anyObject())).andReturn(ptyProcessBuilder);
        EasyMock.expect(ptyProcessBuilder.start()).andReturn(ptyProcess);
        PowerMock.expectNew(PtyProcessBuilder.class).andReturn(ptyProcessBuilder);
        PowerMock.replay(ptyProcessBuilder,PtyProcessBuilder.class);

        ConnectionInfo connectionInfo = new ConnectionInfo(testUserName, testPIDdir, concurrentWebshell);
        connectionInfo.connect();
        connectionInfo.disconnect();
        verifyAll();
    }

    @Test
    public void testConnectFailure() throws Exception{
        thrown.expect(RuntimeIOException.class);

        AtomicInteger concurrentWebshell = EasyMock.createNiceMock(AtomicInteger.class);
        EasyMock.replay(concurrentWebshell);

        PowerMock.expectNew(PtyProcessBuilder.class).andThrow(new IOException());
        PowerMock.replay(PtyProcessBuilder.class);
        ConnectionInfo connectionInfo = new ConnectionInfo("Alice", testPIDdir, concurrentWebshell);
        connectionInfo.connect();
    }
}
