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
package org.apache.knox.gateway.service.admin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.ws.rs.core.MediaType;

import org.apache.knox.gateway.service.definition.ServiceDefinitionPair;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class ServiceDefinitionUnmarshallerTest {

  private static InputStream stream(String xml) {
    return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void testExternalEntityIsNotResolved() throws Exception {
    final File secret = File.createTempFile("xxe-secret", ".txt");
    try {
      Files.write(secret.toPath(), "TOP-SECRET".getBytes(StandardCharsets.UTF_8));

      final String malicious = "<?xml version=\"1.0\"?>"
          + "<!DOCTYPE serviceDefinition [ <!ENTITY xxe SYSTEM \"file://" + secret.getAbsolutePath() + "\"> ]>"
          + "<serviceDefinition><service name=\"test\" role=\"test\" version=\"1.0.0\">"
          + "<testURLs><testURL>&xxe;</testURL></testURLs></service></serviceDefinition>";

      try {
        final ServiceDefinitionPair pair = new ServiceDefinitionUnmarshaller().readFrom(
            ServiceDefinitionPair.class, null, null, MediaType.APPLICATION_XML_TYPE, null, stream(malicious));
        if (pair != null && pair.getService() != null) {
          assertFalse("External entity was resolved - XXE!",
              String.valueOf(pair.getService().getTestURLs()).contains("TOP-SECRET"));
        }
      } catch (IOException rejected) {
        // no-op
      }
    } finally {
      Files.deleteIfExists(secret.toPath());
    }
  }

  @Test
  public void testEntityExpansionIsNotResolved() throws Exception {
    final String malicious = "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE serviceDefinition [ "
        + "<!ENTITY a0 \"EXPANDED-PAYLOAD\">"
        + "<!ENTITY a1 \"&a0;&a0;\">"
        + "<!ENTITY a2 \"&a1;&a1;\">"
        + "<!ENTITY a3 \"&a2;&a2;\"> ]>"
        + "<serviceDefinition><service name=\"test\" role=\"test\" version=\"1.0.0\">"
        + "<testURLs><testURL>&a3;</testURL></testURLs></service></serviceDefinition>";

    try {
      final ServiceDefinitionPair pair = new ServiceDefinitionUnmarshaller().readFrom(
          ServiceDefinitionPair.class, null, null, MediaType.APPLICATION_XML_TYPE, null, stream(malicious));
      if (pair != null && pair.getService() != null) {
        assertFalse("Internal entity was expanded - entity expansion!",
            String.valueOf(pair.getService().getTestURLs()).contains("EXPANDED-PAYLOAD"));
      }
    } catch (IOException rejected) {
      // no-op
    }
  }

  @Test
  public void testValidServiceDefinitionParses() throws Exception {
    final String xml = "<?xml version=\"1.0\"?>"
        + "<serviceDefinition><service name=\"test\" role=\"test\" version=\"1.0.0\"/></serviceDefinition>";

    final ServiceDefinitionPair pair = new ServiceDefinitionUnmarshaller().readFrom(
        ServiceDefinitionPair.class, null, null, MediaType.APPLICATION_XML_TYPE, null, stream(xml));

    assertNotNull(pair);
    assertNotNull(pair.getService());
  }
}
