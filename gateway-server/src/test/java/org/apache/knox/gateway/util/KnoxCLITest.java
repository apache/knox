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
package org.apache.knox.gateway.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.model.DescriptorConfiguration;
import org.apache.knox.gateway.model.ProviderConfiguration;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.apache.knox.test.TestUtils;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import com.nimbusds.jose.JWSAlgorithm;

/**
 * @author larry
 *
 */
public class KnoxCLITest {
  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

  @Before
  public void setUp() throws Exception {
    System.setOut(new PrintStream(outContent, false, StandardCharsets.UTF_8.name()));
    System.setErr(new PrintStream(errContent, false, StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testRemoteConfigurationRegistryClientService() throws Exception {
    outContent.reset();

    KnoxCLI cli = new KnoxCLI();
    Configuration config = new GatewayConfigImpl();
    // Configure a client for the test local filesystem registry implementation
    config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=/test");
    cli.setConf(config);

    // This is only to get the gateway services initialized
    cli.run(new String[]{"version"});

    RemoteConfigurationRegistryClientService service =
                                   cli.getGatewayServices().getService(ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE);
    assertNotNull(service);
    RemoteConfigurationRegistryClient client = service.get("test_client");
    assertNotNull(client);

    assertNull(service.get("bogus"));
  }

  @Test
  public void testListRemoteConfigurationRegistryClients() throws Exception {
    outContent.reset();

    KnoxCLI cli = new KnoxCLI();
    String[] args = { "list-registry-clients", "--master","master" };

    Configuration config = new GatewayConfigImpl();
    cli.setConf(config);

    // Test with no registry clients configured
    int rc = cli.run(args);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).isEmpty());

    // Test with a single client configured
    // Configure a client for the test local filesystem registry implementation
    config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=/test1");
    cli.setConf(config);
    outContent.reset();
    rc = cli.run(args);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("test_client"));

    // Configure another client for the test local filesystem registry implementation
    config.set("gateway.remote.config.registry.another_client", "type=LocalFileSystem;address=/test2");
    cli.setConf(config);
    outContent.reset();
    rc = cli.run(args);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("test_client"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("another_client"));
  }

  @Test
  public void testRemoteConfigurationRegistryGetACLs() throws Exception {
    outContent.reset();


    final File testRoot = TestUtils.createTempDir(this.getClass().getName());
    try {
      final File testRegistry = new File(testRoot, "registryRoot");

      final String providerConfigName = "my-provider-config.xml";
      final String providerConfigContent = "<gateway/>\n";
      final File testProviderConfig = new File(testRoot, providerConfigName);
      final String[] uploadArgs = {"upload-provider-config", testProviderConfig.getAbsolutePath(),
                                   "--registry-client", "test_client",
                                   "--master", "master"};
      FileUtils.writeStringToFile(testProviderConfig, providerConfigContent, StandardCharsets.UTF_8);


      final String[] args = {"get-registry-acl", "/knox/config/shared-providers",
                             "--registry-client", "test_client",
                             "--master", "master"};

      KnoxCLI cli = new KnoxCLI();
      Configuration config = new GatewayConfigImpl();
      // Configure a client for the test local filesystem registry implementation
      config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=" + testRegistry);
      cli.setConf(config);

      int rc = cli.run(uploadArgs);
      assertEquals(0, rc);

      // Run the test command
      rc = cli.run(args);

      // Validate the result
      assertEquals(0, rc);
      String result = outContent.toString(StandardCharsets.UTF_8.name());
      assertEquals(result, 3, result.split("\n").length);
    } finally {
      FileUtils.forceDelete(testRoot);
    }
  }


  @Test
  public void testRemoteConfigurationRegistryUploadProviderConfig() throws Exception {
    outContent.reset();

    final String providerConfigName = "my-provider-config.xml";
    final String providerConfigContent = "<gateway/>\n";

    final File testRoot = TestUtils.createTempDir(this.getClass().getName());
    try {
      final File testRegistry = new File(testRoot, "registryRoot");
      final File testProviderConfig = new File(testRoot, providerConfigName);

      final String[] args = {"upload-provider-config", testProviderConfig.getAbsolutePath(),
                             "--registry-client", "test_client",
                             "--master", "master"};

      FileUtils.writeStringToFile(testProviderConfig, providerConfigContent, StandardCharsets.UTF_8);

      KnoxCLI cli = new KnoxCLI();
      Configuration config = new GatewayConfigImpl();
      // Configure a client for the test local filesystem registry implementation
      config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=" + testRegistry);
      cli.setConf(config);

      // Run the test command
      int rc = cli.run(args);

      // Validate the result
      assertEquals(0, rc);

      outContent.reset();
      final String[] listArgs = {"list-provider-configs", "--registry-client", "test_client"};
      cli.run(listArgs);
      String outStr =  outContent.toString(StandardCharsets.UTF_8.name()).trim();
      assertTrue(outStr.startsWith("Provider Configurations"));
      assertTrue(outStr.endsWith(")\n"+providerConfigName));

      File registryFile = new File(testRegistry, "knox/config/shared-providers/" + providerConfigName);
      assertTrue(registryFile.exists());
      assertEquals(FileUtils.readFileToString(registryFile, StandardCharsets.UTF_8), providerConfigContent);
    } finally {
      FileUtils.forceDelete(testRoot);
    }
  }


  @Test
  public void testRemoteConfigurationRegistryUploadProviderConfigWithDestinationOverride() throws Exception {
    outContent.reset();

    final String providerConfigName = "my-provider-config.xml";
    final String entryName = "my-providers.xml";
    final String providerConfigContent = "<gateway/>\n";

    final File testRoot = TestUtils.createTempDir(this.getClass().getName());
    try {
      final File testRegistry = new File(testRoot, "registryRoot");
      final File testProviderConfig = new File(testRoot, providerConfigName);

      final String[] args = {"upload-provider-config", testProviderConfig.getAbsolutePath(),
                             "--entry-name", entryName,
                             "--registry-client", "test_client",
                             "--master", "master"};

      FileUtils.writeStringToFile(testProviderConfig, providerConfigContent, StandardCharsets.UTF_8);

      KnoxCLI cli = new KnoxCLI();
      Configuration config = new GatewayConfigImpl();
      // Configure a client for the test local filesystem registry implementation
      config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=" + testRegistry);
      cli.setConf(config);

      // Run the test command
      int rc = cli.run(args);

      // Validate the result
      assertEquals(0, rc);
      assertFalse((new File(testRegistry, "knox/config/shared-providers/" + providerConfigName)).exists());
      File registryFile = new File(testRegistry, "knox/config/shared-providers/" + entryName);
      assertTrue(registryFile.exists());
      assertEquals(FileUtils.readFileToString(registryFile, StandardCharsets.UTF_8), providerConfigContent);
    } finally {
      FileUtils.forceDelete(testRoot);
    }
  }


  @Test
  public void testRemoteConfigurationRegistryUploadDescriptor() throws Exception {
    outContent.reset();

    final String descriptorName = "my-topology.json";
    final String descriptorContent = testDescriptorContentJSON;

    final File testRoot = TestUtils.createTempDir(this.getClass().getName());
    try {
      final File testRegistry = new File(testRoot, "registryRoot");
      final File testDescriptor = new File(testRoot, descriptorName);

      final String[] args = {"upload-descriptor", testDescriptor.getAbsolutePath(),
                             "--registry-client", "test_client",
                             "--master", "master"};

      FileUtils.writeStringToFile(testDescriptor, descriptorContent, StandardCharsets.UTF_8);

      KnoxCLI cli = new KnoxCLI();
      Configuration config = new GatewayConfigImpl();
      // Configure a client for the test local filesystem registry implementation
      config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=" + testRegistry);
      cli.setConf(config);

      // Run the test command
      int rc = cli.run(args);

      // Validate the result
      assertEquals(0, rc);

      outContent.reset();
      final String[] listArgs = {"list-descriptors", "--registry-client", "test_client"};
      cli.run(listArgs);
      String outStr =  outContent.toString(StandardCharsets.UTF_8.name()).trim();
      assertTrue(outStr.startsWith("Descriptors"));
      assertTrue(outStr.endsWith(")\n"+descriptorName));

      File registryFile = new File(testRegistry, "knox/config/descriptors/" + descriptorName);
      assertTrue(registryFile.exists());
      assertEquals(FileUtils.readFileToString(registryFile, StandardCharsets.UTF_8), descriptorContent);
    } finally {
      FileUtils.forceDelete(testRoot);
    }
  }

  @Test
  public void testRemoteConfigurationRegistryUploadDescriptorWithDestinationOverride() throws Exception {
    outContent.reset();

    final String descriptorName = "my-topology.json";
    final String entryName = "different-topology.json";
    final String descriptorContent = testDescriptorContentJSON;

    final File testRoot = TestUtils.createTempDir(this.getClass().getName());
    try {
      final File testRegistry = new File(testRoot, "registryRoot");
      final File testDescriptor = new File(testRoot, descriptorName);

      final String[] args = {"upload-descriptor", testDescriptor.getAbsolutePath(),
                             "--entry-name", entryName,
                             "--registry-client", "test_client",
                             "--master", "master"};

      FileUtils.writeStringToFile(testDescriptor, descriptorContent, StandardCharsets.UTF_8);

      KnoxCLI cli = new KnoxCLI();
      Configuration config = new GatewayConfigImpl();
      // Configure a client for the test local filesystem registry implementation
      config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=" + testRegistry);
      cli.setConf(config);

      // Run the test command
      int rc = cli.run(args);

      // Validate the result
      assertEquals(0, rc);
      assertFalse((new File(testRegistry, "knox/config/descriptors/" + descriptorName)).exists());
      File registryFile = new File(testRegistry, "knox/config/descriptors/" + entryName);
      assertTrue(registryFile.exists());
      assertEquals(FileUtils.readFileToString(registryFile, StandardCharsets.UTF_8), descriptorContent);
    } finally {
      FileUtils.forceDelete(testRoot);
    }
  }

  @Test
  public void testRemoteConfigurationRegistryDeleteProviderConfig() throws Exception {
    outContent.reset();

    // Create a provider config
    final String providerConfigName = "my-provider-config.xml";
    final String providerConfigContent = "<gateway/>\n";

    final File testRoot = TestUtils.createTempDir(this.getClass().getName());
    try {
      final File testRegistry = new File(testRoot, "registryRoot");
      final File testProviderConfig = new File(testRoot, providerConfigName);

      final String[] createArgs = {"upload-provider-config", testProviderConfig.getAbsolutePath(),
                                   "--registry-client", "test_client",
                                   "--master", "master"};

      FileUtils.writeStringToFile(testProviderConfig, providerConfigContent, StandardCharsets.UTF_8);

      KnoxCLI cli = new KnoxCLI();
      Configuration config = new GatewayConfigImpl();
      // Configure a client for the test local filesystem registry implementation
      config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=" + testRegistry);
      cli.setConf(config);

      // Run the test command
      int rc = cli.run(createArgs);

      // Validate the result
      assertEquals(0, rc);
      File registryFile = new File(testRegistry, "knox/config/shared-providers/" + providerConfigName);
      assertTrue(registryFile.exists());

      outContent.reset();

      // Delete the created provider config
      final String[] deleteArgs = {"delete-provider-config", providerConfigName,
                                   "--registry-client", "test_client",
                                   "--master", "master"};
      rc = cli.run(deleteArgs);
      assertEquals(0, rc);
      assertFalse(registryFile.exists());

      // Try to delete a provider config that does not exist
      rc = cli.run(new String[]{"delete-provider-config", "imaginary-providers.xml",
                                "--registry-client", "test_client",
                                "--master", "master"});
      assertEquals(0, rc);
    } finally {
      FileUtils.forceDelete(testRoot);
    }
  }

  @Test
  public void testRemoteConfigurationRegistryDeleteDescriptor() throws Exception {
    outContent.reset();

    final String descriptorName = "my-topology.json";

    final File testRoot = TestUtils.createTempDir(this.getClass().getName());
    try {
      final File testRegistry = new File(testRoot, "registryRoot");
      final File testDescriptor = new File(testRoot, descriptorName);

      final String[] createArgs = {"upload-descriptor", testDescriptor.getAbsolutePath(),
                             "--registry-client", "test_client",
                             "--master", "master"};

      FileUtils.writeStringToFile(testDescriptor, testDescriptorContentJSON, StandardCharsets.UTF_8);

      KnoxCLI cli = new KnoxCLI();
      Configuration config = new GatewayConfigImpl();
      // Configure a client for the test local filesystem registry implementation
      config.set("gateway.remote.config.registry.test_client", "type=LocalFileSystem;address=" + testRegistry);
      cli.setConf(config);

      // Run the test command
      int rc = cli.run(createArgs);

      // Validate the result
      assertEquals(0, rc);
      File registryFile = new File(testRegistry, "knox/config/descriptors/" + descriptorName);
      assertTrue(registryFile.exists());

      outContent.reset();

      // Delete the created provider config
      final String[] deleteArgs = {"delete-descriptor", descriptorName,
                                   "--registry-client", "test_client",
                                   "--master", "master"};
      rc = cli.run(deleteArgs);
      assertEquals(0, rc);
      assertFalse(registryFile.exists());

      // Try to delete a descriptor that does not exist
      rc = cli.run(new String[]{"delete-descriptor", "bogus.json",
                                "--registry-client", "test_client",
                                "--master", "master"});
      assertEquals(0, rc);
    } finally {
      FileUtils.forceDelete(testRoot);
    }
  }

  @Test
  public void testSuccessfulAliasLifecycle() throws Exception {
    outContent.reset();
    String[] args1 = {"create-alias", "alias1", "--value", "testvalue1", "--master", "master"};
    int rc;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1 has been successfully " +
        "created."));

    outContent.reset();
    String[] args2 = {"list-alias", "--master",
        "master"};
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1"));

    outContent.reset();
    String[] args4 = {"delete-alias", "alias1", "--master",
      "master"};
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1 has been successfully " +
        "deleted."));

    outContent.reset();
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertFalse(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1"));
  }

  @Test
  public void testListAndDeleteOfAliasForInvalidClusterName() throws Exception {
    outContent.reset();
    String[] args1 =
        { "create-alias", "alias1", "--cluster", "cluster1", "--value", "testvalue1", "--master",
            "master" };
    int rc;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains(
      "alias1 has been successfully " + "created."));

    outContent.reset();
    String[] args2 = { "list-alias", "--cluster", "Invalidcluster1", "--master", "master" };
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()),
      outContent.toString(StandardCharsets.UTF_8.name()).contains("Invalid cluster name provided: Invalidcluster1"));

    outContent.reset();
    String[] args4 =
        { "delete-alias", "alias1", "--cluster", "Invalidcluster1", "--master", "master" };
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()),
      outContent.toString(StandardCharsets.UTF_8.name()).contains("Invalid cluster name provided: Invalidcluster1"));

  }

  @Test
  public void testDeleteOfNonExistAliasFromUserDefinedCluster() throws Exception {
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    try {
      int rc;
      outContent.reset();
      String[] args1 =
          { "create-alias", "alias1", "--cluster", "cluster1", "--value", "testvalue1", "--master",
              "master" };
      cli.run(args1);

      // Delete invalid alias from the cluster
      outContent.reset();
      String[] args2 = { "delete-alias", "alias2", "--cluster", "cluster1", "--master", "master" };
      rc = cli.run(args2);
      assertEquals(0, rc);
      assertTrue(outContent.toString(StandardCharsets.UTF_8.name()).contains("No such alias exists in the cluster."));
    } finally {
      outContent.reset();
      String[] args1 = { "delete-alias", "alias1", "--cluster", "cluster1", "--master", "master" };
      cli.run(args1);
    }
  }

  @Test
  public void testDeleteOfNonExistAliasFromDefaultCluster() throws Exception {
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    try {
      int rc;
      outContent.reset();
      String[] args1 = { "create-alias", "alias1", "--value", "testvalue1", "--master", "master" };
      cli.run(args1);

      // Delete invalid alias from the cluster
      outContent.reset();
      String[] args2 = { "delete-alias", "alias2", "--master", "master" };
      rc = cli.run(args2);
      assertEquals(0, rc);
      assertTrue(outContent.toString(StandardCharsets.UTF_8.name()).contains("No such alias exists in the cluster."));
    } finally {
      outContent.reset();
      String[] args1 = { "delete-alias", "alias1", "--master", "master" };
      cli.run(args1);
    }
  }

  @Test
  public void testForInvalidArgument() throws Exception {
    outContent.reset();
    String[] args1 = { "--value", "testvalue1", "--master", "master" };
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    int rc = cli.run(args1);
    assertEquals(-2, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()).contains("ERROR: Invalid Command"));
  }

  @Test
  public void testListAndDeleteOfAliasForValidClusterName() throws Exception {
    outContent.reset();
    String[] args1 =
        { "create-alias", "alias1", "--cluster", "cluster1", "--value", "testvalue1", "--master",
            "master" };
    int rc;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains(
      "alias1 has been successfully " + "created."));

    outContent.reset();
    String[] args2 = { "list-alias", "--cluster", "cluster1", "--master", "master" };
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1"));

    outContent.reset();
    String[] args4 =
        { "delete-alias", "alias1", "--cluster", "cluster1", "--master", "master" };
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains(
      "alias1 has been successfully " + "deleted."));

    outContent.reset();
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertFalse(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1"));

  }

  @Test
  public void testGatewayAndClusterStores() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );

    outContent.reset();
    String[] gwCreateArgs = {"create-alias", "alias1", "--value", "testvalue1", "--master", "master"};
    int rc;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    rc = cli.run(gwCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1 has been successfully " +
        "created."));

    AliasService as = cli.getGatewayServices().getService(ServiceType.ALIAS_SERVICE);

    outContent.reset();
    String[] clusterCreateArgs = {"create-alias", "alias2", "--value", "testvalue1", "--cluster", "test",
        "--master", "master"};
    cli = new KnoxCLI();
    cli.setConf( config );
    rc = cli.run(clusterCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias2 has been successfully " +
        "created."));

    outContent.reset();
    String[] args2 = {"list-alias", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args2);
    assertEquals(0, rc);
    assertFalse(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias2"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1"));

    char[] passwordChars = as.getPasswordFromAliasForCluster("test", "alias2");
    assertNotNull(passwordChars);
    assertEquals(new String(passwordChars), "testvalue1", new String(passwordChars));

    outContent.reset();
    String[] args1 = {"list-alias", "--cluster", "test", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertFalse(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias2"));

    outContent.reset();
    String[] args4 = {"delete-alias", "alias1", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias1 has been successfully " +
        "deleted."));

    outContent.reset();
    String[] args5 = {"delete-alias", "alias2", "--cluster", "test", "--master", "master"};
    cli = new KnoxCLI();
    rc = cli.run(args5);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("alias2 has been successfully " +
        "deleted."));
  }

  private void createTestMaster() throws Exception {
    outContent.reset();
    String[] args = new String[]{ "create-master", "--master", "master", "--force" };
    KnoxCLI cli = new KnoxCLI();
    int rc = cli.run(args);
    assertThat( rc, is( 0 ) );
    MasterService ms = cli.getGatewayServices().getService(ServiceType.MASTER_SERVICE);
    String master = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master, is( "master" ) );
    assertThat( outContent.toString(StandardCharsets.UTF_8.name()), containsString( "Master secret has been persisted to disk." ) );
  }

  @Test
  public void testCreateSelfSignedCert() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );
    createTestMaster();
    outContent.reset();
    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    String[] gwCreateArgs = {"create-cert", "--hostname", "hostname1", "--master", "master"};
    int rc;
    rc = cli.run(gwCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("gateway-identity has been successfully " +
        "created."));
  }

  @Test
  public void testExportCert() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );
    createTestMaster();
    outContent.reset();
    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    String[] gwCreateArgs = {"create-cert", "--hostname", "hostname1", "--master", "master"};
    int rc;
    rc = cli.run(gwCreateArgs);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("gateway-identity has been successfully " +
        "created."));

    outContent.reset();
    String[] gwCreateArgs2 = {"export-cert", "--type", "PEM"};
    rc = cli.run(gwCreateArgs2);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("Certificate gateway-identity has been successfully exported to"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("gateway-client-trust.pem"));

    // case insensitive
    outContent.reset();
    String[] gwCreateArgs2_6 = {"export-cert", "--type", "pem"};
    rc = cli.run(gwCreateArgs2_6);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("Certificate gateway-identity has been successfully exported to"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("gateway-client-trust.pem"));

    outContent.reset();
    String[] gwCreateArgs2_5 = {"export-cert"};
    rc = cli.run(gwCreateArgs2_5);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("Certificate gateway-identity has been successfully exported to"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("gateway-client-trust.pem"));

    outContent.reset();
    String[] gwCreateArgs3 = {"export-cert", "--type", "JKS"};
    rc = cli.run(gwCreateArgs3);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("Certificate gateway-identity has been successfully exported to"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("gateway-client-trust.jks"));

    // pkcs12
    outContent.reset();
    String[] gwCreateArgs2_7 = {"export-cert", "--type", "pkcs12"};
    rc = cli.run(gwCreateArgs2_7);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("Certificate gateway-identity has been successfully exported to"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("gateway-client-trust.pkcs12"));

    // jceks
    outContent.reset();
    String[] gwCreateArgs2_8 = {"export-cert", "--type", "jceks"};
    rc = cli.run(gwCreateArgs2_8);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("Certificate gateway-identity has been successfully exported to"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("gateway-client-trust.jceks"));

    outContent.reset();
    String[] gwCreateArgs4 = {"export-cert", "--type", "invalid"};
    rc = cli.run(gwCreateArgs4);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("Invalid type for export file provided."));
  }

  @Test
  public void testCreateMaster() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    FileUtils.deleteQuietly( new File( config.getGatewaySecurityDir() ) );
    outContent.reset();
    String[] args = {"create-master", "--master", "master"};
    int rc;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    rc = cli.run(args);
    assertEquals(0, rc);
    MasterService ms = cli.getGatewayServices().getService(ServiceType.MASTER_SERVICE);
    // assertTrue(ms.getClass().getName(), ms.getClass().getName().equals("kjdfhgjkhfdgjkh"));
    assertEquals(new String(ms.getMasterSecret()), "master", new String(ms.getMasterSecret()));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains("Master secret has been persisted to disk."));
  }

  @Test
  public void testCreateMasterGenerate() throws Exception {
    String[] args = {"create-master", "--generate" };
    int rc;
    GatewayConfigImpl config = new GatewayConfigImpl();
    File masterFile = new File( config.getGatewaySecurityDir(), "master" );

    // Need to delete the master file so that the change isn't ignored.
    if( masterFile.exists() ) {
      assertThat( "Failed to delete existing master file.", masterFile.delete(), is( true ) );
    }
    outContent.reset();
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(config);
    rc = cli.run(args);
    assertThat( rc, is( 0 ) );
    MasterService ms = cli.getGatewayServices().getService(ServiceType.MASTER_SERVICE);
    String master = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master.length(), is( 36 ) );
    assertThat( master.indexOf( '-' ), is( 8 ) );
    assertThat( master.indexOf( '-', 9 ), is( 13 ) );
    assertThat( master.indexOf( '-', 14 ), is( 18 ) );
    assertThat( master.indexOf( '-', 19 ), is( 23 ) );
    assertThat( UUID.fromString( master ), notNullValue() );
    assertThat( outContent.toString(StandardCharsets.UTF_8.name()), containsString( "Master secret has been persisted to disk." ) );

    // Need to delete the master file so that the change isn't ignored.
    if( masterFile.exists() ) {
      assertThat( "Failed to delete existing master file.", masterFile.delete(), is( true ) );
    }
    outContent.reset();
    cli = new KnoxCLI();
    rc = cli.run(args);
    ms = cli.getGatewayServices().getService(ServiceType.MASTER_SERVICE);
    String master2 = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master2.length(), is( 36 ) );
    assertThat( UUID.fromString( master2 ), notNullValue() );
    assertThat( master2, not( is( master ) ) );
    assertThat( rc, is( 0 ) );
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("Master secret has been persisted to disk."));
  }

  @Test
  public void testCreateMasterForce() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    File masterFile = new File( config.getGatewaySecurityDir(), "master" );

    // Need to delete the master file so that the change isn't ignored.
    if( masterFile.exists() ) {
      assertThat( "Failed to delete existing master file.", masterFile.delete(), is( true ) );
    }

    KnoxCLI cli = new KnoxCLI();
    cli.setConf(config);
    MasterService ms;
    int rc;
    outContent.reset();

    String[] args = { "create-master", "--master", "test-master-1" };

    rc = cli.run(args);
    assertThat( rc, is( 0 ) );
    ms = cli.getGatewayServices().getService(ServiceType.MASTER_SERVICE);
    String master = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master, is( "test-master-1" ) );
    assertThat( outContent.toString(StandardCharsets.UTF_8.name()), containsString( "Master secret has been persisted to disk." ) );

    outContent.reset();
    rc = cli.run(args);
    assertThat( rc, is(0 ) );
    assertThat( outContent.toString(StandardCharsets.UTF_8.name()), containsString( "Master secret is already present on disk." ) );

    outContent.reset();
    args = new String[]{ "create-master", "--master", "test-master-2", "--force" };
    rc = cli.run(args);
    assertThat( rc, is( 0 ) );
    ms = cli.getGatewayServices().getService(ServiceType.MASTER_SERVICE);
    master = String.copyValueOf( ms.getMasterSecret() );
    assertThat( master, is( "test-master-2" ) );
    assertThat( outContent.toString(StandardCharsets.UTF_8.name()), containsString( "Master secret has been persisted to disk." ) );
  }

  @Test
  public void testListTopology() throws Exception {

    GatewayConfigMock config = new GatewayConfigMock();
    URL topoURL = ClassLoader.getSystemResource("conf-demo/conf/topologies/admin.xml");
    config.setConfDir( new File(topoURL.getFile()).getParentFile().getParent() );
    String[] args = {"list-topologies", "--master", "knox"};

    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );

    cli.run( args );
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("sandbox"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("admin"));
  }

  @Test
  public void testCreateMultipleAliasesInOneBatch() throws Exception {
    outContent.reset();
    String[] args1 = { "create-aliases",
            "--alias", "alias1", "--value", "testvalue1",
            "--alias", "alias2", "--value", "testvalue2",
            "--alias", "alias3", "--value", "testvalue3",
            "--cluster", "cluster1",
            "--master", "master" };
    int rc;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains(
            "3 alias(es) have been successfully created: [alias1, alias2, alias3]"));
  }

  @Test
  public void testCreateAndGenerateMultipleAliasesInOneBatch1() throws Exception {
    outContent.reset();
    String[] args1 = { "create-aliases",
            "--alias", "alias1", "--value", "testvalue1",
            "--alias", "alias2", "--value", "testvalue2",
            "--alias", "alias3",
            "--generate",
            "--cluster", "cluster1",
            "--master", "master" };
    int rc;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains(
            "2 alias(es) have been successfully created: [alias1, alias2]"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains(
            "1 alias(es) have been successfully generated: [alias3]"));
  }

  @Test
  public void testCreateAndGenerateMultipleAliasesInOneBatch2() throws Exception {
    outContent.reset();
    String[] args1 = { "create-aliases",
            "--alias", "alias1", "--value", "testvalue1",
            "--alias", "alias2",
            "--alias", "alias3",
            "--generate",
            "--cluster", "cluster1",
            "--master", "master" };
    int rc;
    KnoxCLI cli = new KnoxCLI();
    cli.setConf(new GatewayConfigImpl());
    rc = cli.run(args1);
    assertEquals(0, rc);
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains(
            "2 alias(es) have been successfully generated: [alias2, alias3]"));
    assertTrue(outContent.toString(StandardCharsets.UTF_8.name()), outContent.toString(StandardCharsets.UTF_8.name()).contains(
            "1 alias(es) have been successfully created: [alias1]"));
  }

  private class GatewayConfigMock extends GatewayConfigImpl{
    private String confDir;
    public void setConfDir(String location) {
      confDir = location;
    }

    @Override
    public String getGatewayConfDir(){
      return confDir;
    }
  }

  private static XMLTag createBadTopology() {
    return XMLDoc.newDocument(true)
        .addRoot( "topology" )
        .addTag( "gateway" )

        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "123" )
        .addTag( "param" )
        .addTag( "name" ).addText( "" )
        .addTag( "value" ).addText( "org.apache.knox.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( "ldap://localhost:8443" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "vvv" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
  }

  private static XMLTag createGoodTopology() {
    return XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )

        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm" )
        .addTag( "value" ).addText( "org.apache.knox.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( "ldap://localhost:8443").gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
  }

  private File writeTestTopology( String name, XMLTag xml ) throws IOException {
    // Create the test topology.

    GatewayConfigMock config = new GatewayConfigMock();
    URL topoURL = ClassLoader.getSystemResource("conf-demo/conf/topologies/admin.xml");
    config.setConfDir( new File(topoURL.getFile()).getParentFile().getParent() );

    File tempFile = new File( config.getGatewayTopologyDir(), name + ".xml." + UUID.randomUUID() );
    try(OutputStream stream = Files.newOutputStream(tempFile.toPath())) {
      xml.toStream(stream);
    }
    File descriptor = new File( config.getGatewayTopologyDir(), name + ".xml" );
    tempFile.renameTo( descriptor );
    return descriptor;
  }

  @Test
  public void testValidateTopology() throws Exception {

    GatewayConfigMock config = new GatewayConfigMock();
    URL topoURL = ClassLoader.getSystemResource("conf-demo/conf/topologies/admin.xml");
    config.setConfDir( new File(topoURL.getFile()).getParentFile().getParent() );
    String[] args = {"validate-topology", "--master", "knox", "--cluster", "sandbox"};

    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    cli.run( args );

    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString(config.getGatewayTopologyDir()));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("sandbox"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("success"));
    outContent.reset();


    String[] args2 = {"validate-topology", "--master", "knox", "--cluster", "NotATopology"};
    cli.run(args2);

    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("NotATopology"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("does not exist"));
    outContent.reset();

    String[] args3 = {"validate-topology", "--master", "knox", "--path", config.getGatewayTopologyDir() + "/admin.xml"};
    cli.run(args3);

    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("admin"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("success"));
    outContent.reset();

    String[] args4 = {"validate-topology", "--master", "knox", "--path", "not/a/path"};
    cli.run(args4);
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("does not exist"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("not/a/path"));
  }

  @Test
  public void testValidateTopologyOutput() throws Exception {

    File bad = writeTestTopology( "test-cluster-bad", createBadTopology() );
    assertNotNull(bad);
    File good = writeTestTopology( "test-cluster-good", createGoodTopology() );
    assertNotNull(good);

    GatewayConfigMock config = new GatewayConfigMock();
    URL topoURL = ClassLoader.getSystemResource("conf-demo/conf/topologies/admin.xml");
    config.setConfDir( new File(topoURL.getFile()).getParentFile().getParent() );
    String[] args = {"validate-topology", "--master", "knox", "--cluster", "test-cluster-bad"};

    KnoxCLI cli = new KnoxCLI();
    cli.setConf( config );
    cli.run( args );

    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString(config.getGatewayTopologyDir()));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("test-cluster-bad"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("unsuccessful"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("Invalid content"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("Line"));


    outContent.reset();

    String[] args2 = {"validate-topology", "--master", "knox", "--cluster", "test-cluster-good"};

    cli.run(args2);

    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString(config.getGatewayTopologyDir()));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("success"));
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("test-cluster-good"));


  }

  /* Test cli command to convert topology to providers and descriptors */
  @Test
  public void testConvertTopology() throws Exception {
    outContent.reset();
    Configuration config = new GatewayConfigImpl();
    URL topologyFileURL = ClassLoader.getSystemResource("token-test.xml");
    final File topologyFile = Paths.get(topologyFileURL.toURI()).toFile();
    final File outputDir = createDir();
    final String providerConfigFileName = "my-provider.json";
    final String descriptorConfigFileName = "my-descriptor.json";
    final String clusterName = "myCluster";
    final String discoveryUrl = "https://localhost:7183";
    final String discoveryUser = "discoveryUser";
    final String discoveryType = "ClouderaManager";
    final String discoveryPwdAlias = "discovery";
    final ObjectMapper mapper = new ObjectMapper();

    try {
      KnoxCLI cli = new KnoxCLI();
      cli.setConf(config);

      // This is only to get the gateway services initialized
      cli.run(new String[]{"convert-topology", "--master", "master",
          "--path", topologyFile.getAbsolutePath(),
          "--provider-name", providerConfigFileName,
          "--descriptor-name", descriptorConfigFileName,
          "--output-dir", outputDir.getAbsolutePath(),
          "--force",
          "--cluster", clusterName,
          "--discovery-url", discoveryUrl,
          "--discovery-user", discoveryUser,
          "--discovery-pwd-alias", discoveryPwdAlias,
          "--discovery-type", discoveryType});

      final File providerConfigFile = new File(outputDir+File.separator+providerConfigFileName);
      final File descriptorConfigFile = new File(outputDir+File.separator+descriptorConfigFileName);

      assertTrue("Provider config file not created", providerConfigFile.exists());
      assertTrue("Descriptor config file not created", descriptorConfigFile.exists());

      final ProviderConfiguration providerJson = mapper.readValue(providerConfigFile, ProviderConfiguration.class);
      final DescriptorConfiguration descriptorJson = mapper.readValue(descriptorConfigFile, DescriptorConfiguration.class);

      assertNotNull("Provider config could not be deserialized", providerJson);
      assertNotNull("Descriptor config could not be deserialized", descriptorJson);

      assertEquals(providerJson.getProviders().size(), 1);
      assertEquals(providerJson.getProviders().get(0).getParams().size(), 8);
      assertEquals(providerJson.getProviders().get(0).getName(), "ShiroProvider");
      assertEquals(providerJson.getProviders().get(0).getRole(), "authentication");
      assertEquals(providerJson.getProviders().get(0).isEnabled(), "true");

      /* test param order */
      assertEquals(providerJson.getProviders().get(0).getParams().get(0).getName(), "sessionTimeout");
      assertEquals(providerJson.getProviders().get(0).getParams().get(3).getName(), "main.ldapRealm.contextFactory");
      assertEquals(providerJson.getProviders().get(0).getParams().get(3).getName(), "main.ldapRealm.contextFactory");
      assertEquals(providerJson.getProviders().get(0).getParams().get(5).getValue(), "ldap://localhost:33389");
      assertEquals(providerJson.getProviders().get(0).getParams().get(7).getValue(), "authcBasic");

      assertEquals(descriptorJson.getDiscoveryType(), discoveryType);
      assertEquals(descriptorJson.getDiscoveryAddress(), discoveryUrl);
      assertEquals(descriptorJson.getDiscoveryPasswordAlias(), discoveryPwdAlias);
      assertEquals(descriptorJson.getDiscoveryUser(), discoveryUser);
      assertEquals(descriptorJson.getCluster(), clusterName);
      assertEquals(descriptorJson.getServices().size(), 1);
      assertEquals(descriptorJson.getServices().get(0).getRole(), "KNOXTOKEN");
      assertEquals(descriptorJson.getServices().get(0).getParams().size(), 5);

    } finally {
      FileUtils.deleteQuietly(outputDir);
    }
  }

  @Test
  public void testGeneratingJwkInvalidAlgorithm() throws Exception {
    outContent.reset();
    final KnoxCLI cli = new KnoxCLI();
    cli.run(new String[] { "generate-jwk", "--jwkAlg", "HS255", "--master", "master" });
    // confirm that the output is the help message
    assertThat(outContent.toString(StandardCharsets.UTF_8.name()), containsString("generate-jwk [--jwkAlg HS256|HS384|HS512]"));
    outContent.reset();
  }

  @Test
  public void testGeneratingJwk256() throws Exception {
    testGeneratingJWK(JWSAlgorithm.HS256);
  }

  @Test
  public void testGeneratingJwk256SavingAsAlias() throws Exception {
    testGeneratingJWK(JWSAlgorithm.HS256, TokenMAC.KNOX_TOKEN_HASH_KEY_ALIAS_NAME);
  }

  @Test
  public void testGeneratingJwk384() throws Exception {
    testGeneratingJWK(JWSAlgorithm.HS384);
  }

  @Test
  public void testGeneratingJwk512() throws Exception {
    testGeneratingJWK(JWSAlgorithm.HS512);
  }

  private void testGeneratingJWK(JWSAlgorithm jwkAlgorithm) throws Exception {
    testGeneratingJWK(jwkAlgorithm, null);
  }

  private void testGeneratingJWK(JWSAlgorithm jwkAlgorithm, String alias) throws Exception {
    outContent.reset();
    final KnoxCLI cli = new KnoxCLI();
    final String[] args = alias == null ? new String[] { "generate-jwk", "--jwkAlg", jwkAlgorithm.getName(), "--master", "master" }
        : new String[] { "generate-jwk", "--jwkAlg", jwkAlgorithm.getName(), "--master", "master", "--saveAlias", alias };
    cli.run(args);
    final String commandOutput = outContent.toString(StandardCharsets.UTF_8.name());
    outContent.reset();

    if (alias == null) {
      // confirm that the output is a generated secret and *NOT* the help message
      assertThat(commandOutput, not(containsString("generate-jwk [--jwkAlg HS256|HS384|HS512]")));
    } else {
      assertThat(commandOutput, containsString(alias + " has been successfully created."));

      final AliasService aliasService = KnoxCLI.getGatewayServices().getService(ServiceType.ALIAS_SERVICE);
      assertNotNull(new String(aliasService.getPasswordFromAliasForGateway(alias)));
    }

  }


  private File createDir() throws IOException {
    return TestUtils
        .createTempDir(this.getClass().getSimpleName() + "-");
  }

  private static final String testDescriptorContentJSON = "{\n" +
                                                          "  \"discovery-address\":\"http://localhost:8080\",\n" +
                                                          "  \"discovery-user\":\"maria_dev\",\n" +
                                                          "  \"discovery-pwd-alias\":\"sandbox.discovery.password\",\n" +
                                                          "  \"provider-config-ref\":\"my-provider-config\",\n" +
                                                          "  \"cluster\":\"Sandbox\",\n" +
                                                          "  \"services\":[\n" +
                                                          "    {\"name\":\"NAMENODE\"},\n" +
                                                          "    {\"name\":\"JOBTRACKER\"},\n" +
                                                          "    {\"name\":\"WEBHDFS\"},\n" +
                                                          "    {\"name\":\"WEBHCAT\"},\n" +
                                                          "    {\"name\":\"OOZIE\"},\n" +
                                                          "    {\"name\":\"WEBHBASE\"},\n" +
                                                          "    {\"name\":\"HIVE\"},\n" +
                                                          "    {\"name\":\"RESOURCEMANAGER\"}\n" +
                                                          "  ]\n" +
                                                          "}";
}
