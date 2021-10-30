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

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.eclipse.jetty.io.RuntimeIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * data structure to store a connection session
 */
public class JSchConnectionInfo extends ConnectionInfo {

    private JSch jsch;
    private Channel channel;
    private Session jschSession;
    private static final Logger LOG = LoggerFactory.getLogger(JSchConnectionInfo.class);
    public JSchConnectionInfo(String username){
        super(username);
        try {
            jsch = new JSch();
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            jschSession = jsch.getSession("knoxui", "localhost", 22);
            jschSession.setConfig(config);
            jschSession.setPassword("knoxui");
        }catch (JSchException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void connect(){
        try {
            jschSession.connect(30000);
            channel = jschSession.openChannel("shell");
            inputStream = channel.getInputStream();
            outputStream = channel.getOutputStream();
            channel.connect(30000);
            String sudoCmd = "exec sudo -u "+ username + " bash -i\nwhoami\n";
            outputStream.write(sudoCmd.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            //checkConnection(username);
        }  catch (JSchException|IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
    private void checkConnection(String username) throws IOException{
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.US_ASCII);
        BufferedReader bufferedReader = new BufferedReader( reader );
        // todo: implementation highly dependent on the output from host
        int numLinesBeforeUsername = 14;
        for (int i=0; i<numLinesBeforeUsername; i++) {
            LOG.info(bufferedReader.readLine());
        }
        String readUsername = bufferedReader.readLine();
        LOG.info("read user: "+readUsername);
        if (!readUsername.equals(username)) {
            LOG.error("Unknown User!");
            throw new RuntimeException();
        }
    }*/

    @Override
    public void disconnect(){
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
        if (channel != null) {
            channel.disconnect();
            jschSession.disconnect();
        }
    }
}
