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
package org.apache.knox.gateway.shell;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class KnoxSessionTest {
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

  @Test
  public void testParsingPublicCertPem() throws Exception {

    final ClientContext context = ClientContext.with("https://localhost:8443/gateway/dt");
    context.connection().withPublicCertPem(PEM);
    KnoxSession session = KnoxSession.login(context);
    session.close();
  }

  @Test
  public void testParsingInvalidPublicCertPem() throws Exception {

    final ClientContext context = ClientContext.with("https://localhost:8443/gateway/dt");
    try {
      context.connection().withPublicCertPem("INVLID-" + PEM);
      KnoxSession session = KnoxSession.login(context);
      fail("Invalid Public Cert should have resulted in CertificateException wrapped by KnoxShellException");
      session.close();
    }
    catch (KnoxShellException e) {
      assertTrue(e.getCause().toString().contains("CertificateException"));
    }
  }

  @Test
  public void testParsingPublicCertPemWithCertDelimiters() throws Exception {

    final ClientContext context = ClientContext.with("https://localhost:8443/gateway/dt");
    try {
      context.connection().withPublicCertPem(KnoxSession.BEGIN_CERTIFICATE + PEM + KnoxSession.END_CERTIFICATE);
      KnoxSession session = KnoxSession.login(context);
      session.close();
    }
    catch (KnoxShellException e) {
      fail("Should have been able to parse cert with BEGIN and END Certificate delimiters.");
    }
  }
}
