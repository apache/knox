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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.i18n.GatewayUtilCommonMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class X509CertificateUtil {

  private static GatewayUtilCommonMessages LOG = MessagesFactory.get(GatewayUtilCommonMessages.class);

  /**
   * Create a self-signed X.509 Certificate
   * @param dn the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
   * @param pair the KeyPair
   * @param days how many days from now the Certificate is valid for
   * @param algorithm the signing algorithm, eg "SHA256withRSA"
   * @return self-signed X.509 certificate
   */
  public static X509Certificate generateCertificate(String dn, KeyPair pair, int days, String algorithm) {
    try {
      Date from = new Date();
      Date to = new Date(from.getTime() + days * 86400000L);

      BigInteger sn = new BigInteger(64, new SecureRandom());
      X500Name issuer = new X500Name(dn);
      X500Name subject = new X500Name(dn);

      X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
          issuer,
          sn,
          from,
          to,
          subject,
          pair.getPublic()
      );

      // Add Subject Alternative Name extension
      List<GeneralName> generalNames = new ArrayList<>();

      // Pull the hostname out of the DN
      String hostname = dn.split(",", 2)[0].split("=", 2)[1].trim();
      if ("localhost".equals(hostname)) {
        // Add short hostname
        String detectedHostname = InetAddress.getLocalHost().getHostName();
        if (detectedHostname != null && !detectedHostname.isEmpty() && Character.isAlphabetic(detectedHostname.charAt(0))) {
          generalNames.add(new GeneralName(GeneralName.dNSName, detectedHostname));
        }

        // Add fully qualified hostname
        String detectedFullyQualifiedHostname = InetAddress.getLocalHost().getCanonicalHostName();
        if (detectedFullyQualifiedHostname != null && !detectedFullyQualifiedHostname.isEmpty()
            && Character.isAlphabetic(detectedFullyQualifiedHostname.charAt(0))
            && !detectedFullyQualifiedHostname.equals(detectedHostname)) {
          generalNames.add(new GeneralName(GeneralName.dNSName, detectedFullyQualifiedHostname));
        }
      }

      if (hostname != null && !hostname.isEmpty() && Character.isAlphabetic(hostname.charAt(0))) {
        generalNames.add(new GeneralName(GeneralName.dNSName, hostname));
      }

      if (!generalNames.isEmpty()) {
        GeneralNames subjectAltNames = new GeneralNames(generalNames.toArray(new GeneralName[0]));
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
      }

      ContentSigner signer = new JcaContentSignerBuilder(algorithm).build(pair.getPrivate());
      X509CertificateHolder certHolder = certBuilder.build(signer);

      return new JcaX509CertificateConverter().getCertificate(certHolder);

    } catch (Exception e) {
      LOG.failedToGenerateCertificate(e);
      return null;
    }
  }

  public static void writeCertificateToFile(Certificate cert, final File file)
          throws CertificateEncodingException, IOException {
      writeCertificatesToFile(new Certificate[] {cert}, file);
  }

  public static void writeCertificatesToFile(Certificate[] certs, final File file)
      throws CertificateEncodingException, IOException {
    final Base64 encoder = new Base64( 76, "\n".getBytes( StandardCharsets.US_ASCII ) );
    try(OutputStream out = Files.newOutputStream(file.toPath()) ) {
        for (Certificate cert : certs) {
            saveCertificate(out, cert.getEncoded(), encoder);
        }
    }
  }

  private static void saveCertificate(OutputStream out, byte[] bytes, Base64 encoder) throws IOException {
      out.write( "-----BEGIN CERTIFICATE-----\n".getBytes( StandardCharsets.US_ASCII ) );
      out.write( encoder.encodeToString( bytes ).getBytes( StandardCharsets.US_ASCII ) );
      out.write( "-----END CERTIFICATE-----\n".getBytes( StandardCharsets.US_ASCII ) );
  }

  /*
   * Writes one certificate into the given keystore file protected by the default password
   */
  private static void writeCertificateToKeyStore(Certificate cert, final File file, String type)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    writeCertificateToKeyStore(cert, file, type, null);
  }

  /*
   * Writes one certificate into the given keystore file protected by the given password
   */
  private static void writeCertificateToKeyStore(Certificate cert, final File file, String type, String keystorePassword)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    writeCertificatesToKeyStore(new Certificate[] { cert }, file, type, keystorePassword);
  }

  /*
   * Writes an arbitrary number of certificates into the given keystore file protected by the given password
   */
  private static void writeCertificatesToKeyStore(Certificate[] certs, final File file, String type, String keystorePassword)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    if (certs != null) {
      KeyStore ks = KeyStore.getInstance(type);

      char[] password = keystorePassword == null ? "changeit".toCharArray() : keystorePassword.toCharArray();
      ks.load(null, password);
      int counter = 0;
      for (Certificate cert : certs) {
        ks.setCertificateEntry("gateway-identity" + (++counter), cert); //it really does not matter what we set as alias for the certificate
      }
      /* Coverity Scan CID 1361992 */
      try (OutputStream fos = Files.newOutputStream(file.toPath())) {
        ks.store(fos, password);
      }
    }
  }

  public static void writeCertificateToJks(Certificate cert, final File file)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    writeCertificateToKeyStore(cert, file, "jks");
  }

  public static void writeCertificateToJks(Certificate cert, final File file, String keystorePassword)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    writeCertificateToKeyStore(cert, file, "jks", keystorePassword);
  }

  public static void writeCertificatesToJks(Certificate[] certs, final File file, String keystorePassword)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    writeCertificatesToKeyStore(certs, file, "jks", keystorePassword);
  }

  public static void writeCertificateToJceks(Certificate cert, final File file)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    writeCertificateToKeyStore(cert, file, "jceks");
  }

  public static void writeCertificateToPkcs12(Certificate cert, final File file)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    writeCertificateToKeyStore(cert, file, "pkcs12");
  }

  /**
   * Tests the X509 certificate to see if it was self-signed.
   * <p>
   * The certificate is determined to be self-signed if the subject DN is the same as the issuer DN
   *
   * @param certificate the {@link X509Certificate} to test
   * @return <code>true</code> if the X509 certficate is self-signed; otherwise <code>false</code>
   */
  public static boolean isSelfSignedCertificate(Certificate certificate) {
    if (certificate instanceof X509Certificate) {
      X509Certificate x509Certificate = (X509Certificate) certificate;
      return x509Certificate.getSubjectX500Principal().equals(x509Certificate.getIssuerX500Principal());
    } else {
      return false;
    }
  }

  public static X509Certificate[] fetchPublicCertsFromServer(String serverUrl, boolean forceReturnCert, PrintStream out) throws Exception {
      final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore) null);
      final X509TrustManager defaultTrustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
      final CertificateChainAwareTrustManager trustManagerWithCertificateChain = new CertificateChainAwareTrustManager(defaultTrustManager);
      final SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] { trustManagerWithCertificateChain }, null);

      final URL url = new URL(serverUrl);
      final int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
      logOutput(out, "Opening connection to " + url.getHost() + ":" + port + "...");
      try (Socket socket = sslContext.getSocketFactory().createSocket(url.getHost(), port)) {
        socket.setSoTimeout(10000);
        logOutput(out, "Starting SSL handshake...");
        ((SSLSocket) socket).startHandshake();
        logOutput(out, "No errors, certificate is already trusted");
        if (!forceReturnCert) {
            return null; //we already trust the given site's certs; it does not make sense to build a new truststore
        }
      } catch (SSLException e) {
        // NOP; this is expected in case the gateway server's certificate is not in the
        // trust store the JVM uses
      }

      return trustManagerWithCertificateChain.serverCertificateChain;
  }

  private static void logOutput(PrintStream out, String message) {
      if (out != null) {
          out.println(message);
      }
  }

  private static class CertificateChainAwareTrustManager implements X509TrustManager {
      private final X509TrustManager defaultTrustManager;
      private X509Certificate[] serverCertificateChain;

      CertificateChainAwareTrustManager(X509TrustManager tm) {
        this.defaultTrustManager = tm;
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager.getAcceptedIssuers();
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType);
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        this.serverCertificateChain = chain;
        defaultTrustManager.checkServerTrusted(chain, authType);
      }
    }
}
