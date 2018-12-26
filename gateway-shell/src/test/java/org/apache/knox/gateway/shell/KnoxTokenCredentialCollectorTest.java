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
package org.apache.knox.gateway.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.util.JsonUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KnoxTokenCredentialCollectorTest {
  public static final String PEM = "MIICOjCCAaOgAwIBAgIJAN5kp1oW3Up8MA0GCSqGSIb3DQEBBQUAMF8xCzAJBgNVBAYTAlVTMQ0w\n"
      + "CwYDVQQIEwRUZXN0MQ0wCwYDVQQHEwRUZXN0MQ8wDQYDVQQKEwZIYWRvb3AxDTALBgNVBAsTBFRl\n"
      + "c3QxEjAQBgNVBAMTCWxvY2FsaG9zdDAeFw0xODEyMTMwMzE2MTFaFw0xOTEyMTMwMzE2MTFaMF8x\n"
      + "CzAJBgNVBAYTAlVTMQ0wCwYDVQQIEwRUZXN0MQ0wCwYDVQQHEwRUZXN0MQ8wDQYDVQQKEwZIYWRv\n"
      + "b3AxDTALBgNVBAsTBFRlc3QxEjAQBgNVBAMTCWxvY2FsaG9zdDCBnzANBgkqhkiG9w0BAQEFAAOB\n"
      + "jQAwgYkCgYEAqxnzKNhNgPEeOWsTabaxR9N3QjKohvDOrAwwVvzVhHIb1GKRo+TfSkDozS3BmzuO\n"
      + "+xQN6LvIsE6pzl+TFvTJvM9Ir5vMyybww8ZVkeD7vaHvBT9+w+1R79wYEhC7kqj68bGJJpl+1fGa\n"
      + "c6yTKBYcAs3hO54Zg56rgreQKwXeBysCAwEAATANBgkqhkiG9w0BAQUFAAOBgQACFpBmy7KgSiBG\n"
      + "0flF1+l8KXCU7t3LL8F3RlJSF4fyexfojilkHW7u6TdJbrAsz5nhe85AchFl6/jtmvCMGMFPobMI\n"
      + "f/44w9sYdC3u604wJy8CF5xKqDb/en4xmiLnEc0LzOeEvtFv0ociu82SuRara7ua1J6UR9JsNu5p\n"
      + "dWEFEA==\n";

  public static final String JWT = "eyJhbGciOiJSUzI1NiJ9."
      + "eyJzdWIiOiJndWVzdCIsImF1ZCI6InRva2VuYmFzZWQiLCJpc3MiOiJLTk9YU1NPIiwiZXhwIjoxNT"
      + "Q0ODMxNTI3fQ.gcIuNQN1_6dF6guk_7-QZo13xQMtlhtrc53H0lBzhj4Ft8OjUw-QNNMz6-bohz5Al"
      + "XBF6r_whfqFBm8MZUHIh8-hmqt91458acqR3jtJNDrjs5cv2ExaycK40KgyX58cnh6wfph5RLgiAo4"
      + "j3zRSOaykZBq8W1DhYliXkRBFm1w";

  private static final File tokenCacheBackup = new File("tokenCacheBackup.bin");

  @BeforeClass
  public static void backupTokenCache() throws Exception {
    File tokenCacheFile = getTokenCacheFile();
    if (tokenCacheFile.exists()) {
      FileUtils.copyFile(getTokenCacheFile(), tokenCacheBackup);
    }
  }

  @AfterClass
  public static void restoreTokenCache() throws Exception {
    if (tokenCacheBackup.exists()) {
      FileUtils.moveFile(tokenCacheBackup, getTokenCacheFile());
      tokenCacheBackup.delete();
    }
  }

  private static File getTokenCacheFile() throws Exception {
    return new File(System.getProperty("user.home"), getTokenCacheFileName());
  }

  private static String getTokenCacheFileName() throws Exception {
    Field f = KnoxTokenCredentialCollector.class.getDeclaredField("KNOXTOKENCACHE");
    f.setAccessible(true);
    Object fieldValue = f.get(KnoxTokenCredentialCollector.class);
    assertTrue(fieldValue instanceof String);
    return (String) fieldValue;
  }

  /**
   * KNOX-1680
   */
  @Test
  public void testEmptyTokenCache() {

    // Delete the existing token cache file, and replace it with an empty one
    try {
      File tokenCacheFile = getTokenCacheFile();
      if (tokenCacheFile.exists()) {
        tokenCacheFile.delete();
      }
      assertTrue(tokenCacheFile.createNewFile());
    } catch (Exception e) {
      // If the empty file could not be created, then this test does not make
      // any sense.
      fail("Could not create empty Knox token cache file: " + e.getMessage());
    }

    boolean caughtCredentialCollectionException = false;

    // Attempt to collect the Knox token
    KnoxTokenCredentialCollector collector = new KnoxTokenCredentialCollector();
    try {
      collector.collect();
    } catch (CredentialCollectionException e) {
      // Expected
      caughtCredentialCollectionException = true;
    } catch (Exception e) {
      fail("Unexpected exception: " + e.getMessage());
    } finally {
      try {
        getTokenCacheFile().delete();
      } catch (Exception e) {
        // Ignore
      }
    }

    assertTrue("Expected exception not thrown.", caughtCredentialCollectionException);
  }

  @Test
  public void testParsingPublicCertPem() throws Exception {
    Map<String, Object> map = new HashMap<>();
    map.put("access_token", JWT);
    map.put("target_url", "https://localhost:8443/gateway/sandbox" );
    map.put("token_type", "Bearer");
    map.put("endpoint_public_cert", PEM);

    // NOTE: we are setting the expiry to -1 however this is not the actual expiration inside
    // the JWT. Any tests that are added to test that the token is not meant to expire will fail.
    map.put("expires_in", -1L);
    Credentials credentials = new org.apache.knox.gateway.shell.Credentials();
    KnoxTokenCredentialCollector knoxTokenCollector = new KnoxTokenCredentialCollector() {
      @Override
      protected String getCachedKnoxToken() throws IOException {
        return JsonUtils.renderAsJsonString(map);
      }
    };

    credentials.add(knoxTokenCollector, "none: ", "token");
    credentials.collect();

    KnoxTokenCredentialCollector token = (KnoxTokenCredentialCollector) credentials.get("token");

    assertEquals(token.string(), map.get("access_token"));
    assertEquals(token.getTargetUrl(), map.get("target_url"));
    assertEquals(token.getTokenType(), map.get("token_type"));
    assertEquals(token.getEndpointClientCertPEM(), map.get("endpoint_public_cert"));
    assertEquals(token.getExpiresIn(), map.get("expires_in"));
  }

}
