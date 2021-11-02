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

import org.eclipse.jetty.io.RuntimeIOException;
import de.thetaphi.forbiddenapis.SuppressForbidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
* data structure to store a connection session
*/
public class ProcessConnectionInfo extends ConnectionInfo {


    private Process process;
    private static final Logger LOG = LoggerFactory.getLogger(ProcessConnectionInfo.class);

    public ProcessConnectionInfo(String username){
        super(username);
    }

    @SuppressForbidden
    @Override
    public void connect(){
        try {
            // todo: make it compatible with windows, redhat, centos
            ProcessBuilder builder = new ProcessBuilder( "sudo","-u",username,"bash","-i");
            builder.redirectErrorStream(true); // combine stderr with stdout
            process = builder.start();
            inputStream = process.getInputStream();
            outputStream = process.getOutputStream();
            //job control is disabled by default, can use 'set -m' to enable job control
            outputStream.write("cd $HOME\nwhoami\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            LOG.info("started bash session for " + username);
        } catch(IOException e) {
            LOG.error("Error starting bash for " + username +" : "+ e.getMessage());
            disconnect();
        }
    }

    public Process getProcess(){
        return this.process;
    }

    @Override
    public void disconnect(){
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        } catch (IOException e){
            throw new RuntimeIOException(e);
        }
        if (process != null) {
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
