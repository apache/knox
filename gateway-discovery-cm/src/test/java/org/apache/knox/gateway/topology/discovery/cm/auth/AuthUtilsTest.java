/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.auth;

import org.apache.knox.gateway.config.GatewayConfig;
import org.junit.Test;

import javax.security.auth.login.Configuration;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class AuthUtilsTest {

  /**
   * Since the login config should only be loaded once, the Configuration object should be the same for repeated
   * requests thereof.
   *
   * KNOX-1962
   */
  @Test
  public void testLoadJAASConfigOnce() {
    File loginConfigFile = createTestKrb5LoginConfigFile();
    Configuration conf1 = null;
    Configuration conf2 = null;
    try {
      System.setProperty(GatewayConfig.KRB5_LOGIN_CONFIG, loginConfigFile.getAbsolutePath());
      conf1 = AuthUtils.getKerberosJAASConfiguration();
      conf2 = AuthUtils.getKerberosJAASConfiguration();
    } catch (Throwable e) {
      fail(e.getMessage());
    } finally {
      System.clearProperty(GatewayConfig.KRB5_LOGIN_CONFIG);
    }

    assertNotNull(conf1);
    assertNotNull(conf2);
    assertSame(conf1, conf2);
  }

  private static File createTestKrb5LoginConfigFile() {
    File result = null;
    try {
      File f = File.createTempFile("krb5JAASLogin", ".conf");
      f.deleteOnExit();
      try(OutputStream out = Files.newOutputStream(f.toPath())) {
        out.write(getTestKrb5LoginConf().getBytes(StandardCharsets.UTF_8));
        out.flush();
      }
      result = f;
    } catch (Exception e) {
      //
    }

    return result;
  }

  private static String getTestKrb5LoginConf() {
    return AuthUtils.JGSS_LOGIN_MODULE + " {\n" +
           "com.sun.security.auth.module.Krb5LoginModule required\n" +
           "renewTGT=false\n" +
           "doNotPrompt=true\n" +
           "useKeyTab=false\n" +
           "principal=\"knox/myhost.example.com@EXAMPLE.COM\"\n" +
           "storeKey=false\n" +
           "useTicketCache=false;\n" +
           "};";
  }

}
