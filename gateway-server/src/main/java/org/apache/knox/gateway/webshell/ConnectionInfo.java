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
import de.thetaphi.forbiddenapis.SuppressForbidden;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.websockets.WebsocketLogMessages;
import org.eclipse.jetty.io.RuntimeIOException;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
* data structure to store a connection session
*/
public class ConnectionInfo {
    private static final Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(
            AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
            AuditConstants.KNOX_COMPONENT_NAME );
    private static final WebsocketLogMessages LOG = MessagesFactory.get(WebsocketLogMessages.class);
    private static final String TERMINAL_EMULATOR_KEY = "TERM";
    private static final String TERMINAL_EMULATOR_VALUE = "xterm";

    private InputStream inputStream;
    private OutputStream outputStream;
    private PtyProcess ptyProcess;
    private final String username;

    private final String gatewayPIDDir;
    @SuppressWarnings("PMD.DoNotUseThreads") //we need to define a Thread to clean up resources using shutdown hook
    private final Thread shutdownHook;
    private final AtomicInteger concurrentWebshells;
    private long pid;
    private final Map<String, String> webshellEnvVariables = new HashMap();

    @SuppressWarnings("PMD.DoNotUseThreads") //we need to define a Thread to clean up resources using shutdown hook
    public ConnectionInfo(String username, String gatewayPIDDir, AtomicInteger concurrentWebshells) {
        this.username = username;
        this.gatewayPIDDir = gatewayPIDDir;
        this.concurrentWebshells = concurrentWebshells;
        shutdownHook = new Thread(this::disconnect);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        webshellEnvVariables.put(TERMINAL_EMULATOR_KEY, TERMINAL_EMULATOR_VALUE);
    }

    private void saveProcessPID(long pid){
        File file = new File(gatewayPIDDir + "/" + "webshell_" + pid + ".pid");
        try {
            FileUtils.writeStringToFile(file, String.valueOf(pid), StandardCharsets.UTF_8);
        } catch (IOException e){
            LOG.onError("error saving PID for webshell:" + e);
        }
        auditor.audit( Action.WEBSHELL, username+':'+pid,
                ResourceType.PROCESS, ActionOutcome.SUCCESS,"Started Bash process");
    }

    @SuppressForbidden // we need to spawn a bash process for authenticated user
    @SuppressWarnings("PMD.DoNotUseThreads") // we need to define a Thread to register a shutdown hook
    public void connect(){
        // sudoers file needs to be configured for this to work.
        // refer to design doc for details
        String[] cmd = { "sudo","--user", username,"bash","-i"};
        try {
            ptyProcess = new PtyProcessBuilder()
                    .setCommand(cmd)
                    .setRedirectErrorStream(true)
                    .setWindowsAnsiColorEnabled(true)
                    // see discussion regarding tty size in design doc
                    .setInitialColumns(100)
                    .setInitialRows(40)
                    //.setEnvironment(env)
                    .start();
        } catch (IOException e) {
            LOG.onError("Error starting ptyProcess: " + e.getMessage());
            disconnect();
            throw new RuntimeIOException(e);
        }
        outputStream = ptyProcess.getOutputStream();
        inputStream = ptyProcess.getInputStream();
        pid = ptyProcess.pid();
        saveProcessPID(pid);
        concurrentWebshells.incrementAndGet();
    }

    public String getUsername(){
        return this.username;
    }
    public long getPid(){
        return this.pid;
    }
    public InputStream getInputStream(){
        return this.inputStream;
    }
    public OutputStream getOutputStream(){
        return this.outputStream;
    }

    public void disconnect(){
        if (ptyProcess != null) {
            ptyProcess.destroy();
            if (ptyProcess.isAlive()) {
                ptyProcess.destroyForcibly();
            }
            ptyProcess = null;
            concurrentWebshells.decrementAndGet();
            auditor.audit( Action.WEBSHELL, username+':'+pid,
                    ResourceType.PROCESS, ActionOutcome.SUCCESS,"destroyed Bash process");
            File fileToDelete = FileUtils.getFile(gatewayPIDDir + "/" + "webshell_" + pid + ".pid");
            FileUtils.deleteQuietly(fileToDelete);
        }


        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e){
            throw new RuntimeIOException(e);
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }
    }
}
