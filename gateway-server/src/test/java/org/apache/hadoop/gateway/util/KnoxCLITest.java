/**
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
package org.apache.hadoop.gateway.util;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.hadoop.gateway.services.security.AliasService;

import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.MasterService;
import org.apache.hadoop.gateway.services.security.impl.CLIMasterService;
import org.apache.hadoop.gateway.config.impl.GatewayConfigImpl;
import org.junit.Before;
import org.junit.Test;

/**
 * @author larry
 *
 */
public class KnoxCLITest {
  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

  @Before
  public void setup() throws Exception {
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }
  
  @Test
  public void testSuccessfulAlaisLifecycle() throws Exception {
    outContent.reset();
    String[] args1 = {"create-alias", "alias1", "--value", "testvalue1", "--master", "master"};
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1 has been successfully " +
        "created."));

    outContent.reset();
    String[] args2 = {"list-alias", "--master", 
        "master"};
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1"));

    outContent.reset();
    String[] args4 = {"delete-alias", "alias1", "--master", 
      "master"};
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1 has been successfully " +
        "deleted."));

    outContent.reset();
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertFalse(outContent.toString(), outContent.toString().contains("alias1"));
  }
  
  @Test
  public void testGatewayAndClusterStores() throws Exception {
    outContent.reset();
    String[] gwCreateArgs = {"create-alias", "alias1", "--value", "testvalue1", "--master", "master"};
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(gwCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1 has been successfully " +
        "created."));

	AliasService as = cli.getGatewayServices().getService(GatewayServices.ALIAS_SERVICE);

    outContent.reset();
    String[] clusterCreateArgs = {"create-alias", "alias2", "--value", "testvalue1", "--cluster", "test", 
        "--master", "master"};
    cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(clusterCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias2 has been successfully " +
        "created."));

    outContent.reset();
    String[] args2 = {"list-alias", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertFalse(outContent.toString(), outContent.toString().contains("alias2"));
    assertTrue(outContent.toString(), outContent.toString().contains("alias1"));

	char[] passwordChars = as.getPasswordFromAliasForCluster("test", "alias2");
	assertNotNull(passwordChars);
	assertTrue(new String(passwordChars), "testvalue1".equals(new String(passwordChars)));

    outContent.reset();
    String[] args1 = {"list-alias", "--cluster", "test", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertFalse(outContent.toString(), outContent.toString().contains("alias1"));
    assertTrue(outContent.toString(), outContent.toString().contains("alias2"));

    outContent.reset();
    String[] args4 = {"delete-alias", "alias1", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias1 has been successfully " +
        "deleted."));
    
    outContent.reset();
    String[] args5 = {"delete-alias", "alias2", "--cluster", "test", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args5);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("alias2 has been successfully " +
        "deleted."));
  }
  
  @Test
  public void testCreateSelfSignedCert() throws Exception {
    outContent.reset();
    String[] gwCreateArgs = {"create-cert", "--hostname", "hostname1", "--master", "master"};
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(gwCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("gateway-identity has been successfully " +
        "created."));
  }

  @Test
  public void testCreateMaster() throws Exception {
    outContent.reset();
    String[] args = {"create-master", "--master", "master"};
    int rc = 0;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args);
	MasterService ms = cli.getGatewayServices().getService("MasterService");
	// assertTrue(ms.getClass().getName(), ms.getClass().getName().equals("kjdfhgjkhfdgjkh"));
	assertTrue(new String(ms.getMasterSecret()), "master".equals(new String(ms.getMasterSecret())));
    assertEquals(0, rc);
    assertTrue(outContent.toString(), outContent.toString().contains("Master secret has been persisted to disk."));
  }
}
