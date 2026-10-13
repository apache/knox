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

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class TruststoreSSLContextUtils {
  private static final GatewaySpiMessages LOGGER = MessagesFactory.get(GatewaySpiMessages.class);

  /* How far down a cause chain to walk before giving up. */
  private static final int MAX_CAUSE_DEPTH = 20;

  private TruststoreSSLContextUtils() {
  }

  public static SSLContext getTruststoreSSLContext(KeyStore truststore) {
    SSLContext sslContext = null;
    try {
      if (truststore != null) {
        SSLContextBuilder sslContextBuilder = SSLContexts.custom();
        sslContextBuilder.loadTrustMaterial(truststore, null);
        sslContext = sslContextBuilder.build();
      }
    } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
      LOGGER.failedToLoadTruststore(e.getMessage(), e);
    }
    return sslContext;
  }

  public static X509TrustManager getTrustManager(KeyStore truststore) {
    try {
      if (truststore != null) {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(truststore);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers != null) {
          for (TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
              return (X509TrustManager) tm;
            }
          }
        }
        throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
      }
    } catch (KeyStoreException | NoSuchAlgorithmException | IllegalStateException e) {
      LOGGER.failedToLoadTruststore(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Whether a failure carries a TLS/trust failure ({@link SSLException} or
   * {@link CertificateException}) anywhere in its cause chain. Timeouts, refused
   * connections and HTTP errors are not TLS trust failures.
   *
   * @param failure the failure to inspect
   * @return {@code true} if the cause chain carries a TLS/trust failure
   */
  public static boolean isTlsTrustFailure(final Throwable failure) {
    Throwable cause = failure;
    for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (cause instanceof SSLException || cause instanceof CertificateException) {
        return true;
      }
      cause = cause.getCause() == cause ? null : cause.getCause();
    }
    return false;
  }
}
