/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public class TruststoreSSLContextUtils {
  private static final GatewaySpiMessages LOGGER = MessagesFactory.get(GatewaySpiMessages.class);

  private TruststoreSSLContextUtils() {
  }

  public static SSLContext getTruststoreSSLContext(KeystoreService keystoreService) {
    SSLContext sslContext = null;
    try {
      if(keystoreService != null) {
        KeyStore truststore = keystoreService.getTruststoreForHttpClient();
        if (truststore != null) {
          SSLContextBuilder sslContextBuilder = SSLContexts.custom();
          sslContextBuilder.loadTrustMaterial(truststore, null);
          sslContext = sslContextBuilder.build();
        }
      }
    } catch (KeystoreServiceException | NoSuchAlgorithmException | KeyStoreException
                 | KeyManagementException e) {
      LOGGER.failedToLoadTruststore(e.getMessage(), e);
    }
    return sslContext;
  }
}
