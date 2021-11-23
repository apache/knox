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

import org.apache.knox.gateway.websockets.WebsocketLogMessages;
import org.eclipse.jetty.io.RuntimeIOException;
import de.thetaphi.forbiddenapis.SuppressForbidden;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
* data structure to store a connection session
*/
public class ConnectionInfo {

    private InputStream inputStream;
    private OutputStream outputStream;
    private Process process;
    private String username;
    private WebsocketLogMessages LOG;

    public ConnectionInfo(String username, WebsocketLogMessages LOG){
        this.username = username;
        this.LOG = LOG;
    }

    @SuppressForbidden
    public void connect(){
        try {
            //ProcessBuilder builder = new ProcessBuilder( "bash","-i");
            ProcessBuilder builder = new ProcessBuilder( "sudo","-u",username,"bash","-i");
            //todo: save pid to local
            builder.redirectErrorStream(true); // combine stderr with stdout
            process = builder.start();
            inputStream = process.getInputStream();
            outputStream = process.getOutputStream();
            outputStream.write("cd $HOME\nwhoami\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            LOG.debugLog("started bash session for " + username);
        } catch(IOException e) {
            LOG.onError("Error starting bash for " + username +" : "+ e.getMessage());
            disconnect();
        }
    }

    public String getUsername(){
        return this.username;
    };

    public InputStream getInputStream(){
        return this.inputStream;
    };

    public OutputStream getOutputStream(){
        return this.outputStream;
    };

    public Process getProcess(){
        return this.process;
    }

    public void disconnect(){
        LOG.debugLog("disconnect bash process for user: "+ username);
        //todo: delete pid
        if (process != null) {
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        } catch (IOException e){
            throw new RuntimeIOException(e);
        }
    }
}
