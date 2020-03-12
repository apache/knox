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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.i18n.GatewayUtilCommonMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class X509CertificateUtil {

  private static GatewayUtilCommonMessages LOG = MessagesFactory.get(GatewayUtilCommonMessages.class);

  /**
   * Create a self-signed X.509 Certificate
   * @param dn the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
   * @param pair the KeyPair
   * @param days how many days from now the Certificate is valid for
   * @param algorithm the signing algorithm, eg "SHA1withRSA"
   * @return self-signed X.509 certificate
   */
  public static X509Certificate generateCertificate(String dn, KeyPair pair, int days, String algorithm) {
    PrivateKey privkey = pair.getPrivate();
    Object x509CertImplObject = null;
    try {
      Date from = new Date();
      Date to = new Date(from.getTime() + days * 86400000L);

      Class<?> certInfoClass = Class.forName(getX509CertInfoModuleName());
      Constructor<?> certInfoConstr = certInfoClass.getConstructor();
      Object certInfoObject = certInfoConstr.newInstance();

      // CertificateValidity interval = new CertificateValidity(from, to);
      Class<?> certValidityClass = Class.forName(getX509CertifValidityModuleName());
      Constructor<?> certValidityConstr = certValidityClass.getConstructor(Date.class, Date.class);
      Object certValidityObject = certValidityConstr.newInstance(from, to);

      BigInteger sn = new BigInteger(64, new SecureRandom());

      // X500Name owner = new X500Name(dn);
      Class<?> x500NameClass = Class.forName(getX509X500NameModuleName());
      Constructor<?> x500NameConstr = x500NameClass.getConstructor(String.class);
      Object x500NameObject = x500NameConstr.newInstance(dn);

      Method methodSET = certInfoObject.getClass().getMethod("set", String.class, Object.class);

      // info.set(X509CertInfo.VALIDITY, interval);
      methodSET.invoke(certInfoObject, getSetField(certInfoObject, "VALIDITY"),certValidityObject);

      // info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
      Class<?> certificateSerialNumberClass = Class.forName(getCertificateSerialNumberModuleName());
      Constructor<?> certificateSerialNumberConstr = certificateSerialNumberClass
                                                         .getConstructor(BigInteger.class);
      Object certificateSerialNumberObject = certificateSerialNumberConstr.newInstance(sn);
      methodSET.invoke(certInfoObject, getSetField(certInfoObject, "SERIAL_NUMBER"),
          certificateSerialNumberObject);

      // info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
      try {
        Class<?> certificateSubjectNameClass = Class.forName(getCertificateSubjectNameModuleName());
        Constructor<?> certificateSubjectNameConstr = certificateSubjectNameClass
                                                          .getConstructor(x500NameClass);
        Object certificateSubjectNameObject = certificateSubjectNameConstr
                                                  .newInstance(x500NameObject);
        methodSET.invoke(certInfoObject, getSetField(certInfoObject, "SUBJECT"),
            certificateSubjectNameObject);
      }
      catch (InvocationTargetException ite) {
        methodSET.invoke(certInfoObject, getSetField(certInfoObject, "SUBJECT"),
            x500NameObject);
      }

      // info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
      try {
        Class<?> certificateIssuerNameClass = Class.forName(getCertificateIssuerNameModuleName());
        Constructor<?> certificateIssuerNameConstr = certificateIssuerNameClass
                                                         .getConstructor(x500NameClass);
        Object certificateIssuerNameObject = certificateIssuerNameConstr.newInstance(x500NameObject);
        methodSET.invoke(certInfoObject, getSetField(certInfoObject, "ISSUER"),
            certificateIssuerNameObject);
      }
      catch (InvocationTargetException ite) {
        methodSET.invoke(certInfoObject, getSetField(certInfoObject, "ISSUER"),
            x500NameObject);
      }

      // info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
      Class<?> certificateX509KeyClass = Class.forName(getCertificateX509KeyModuleName());
      Constructor<?> certificateX509KeyConstr = certificateX509KeyClass
                                                    .getConstructor(PublicKey.class);
      Object certificateX509KeyObject = certificateX509KeyConstr.newInstance(pair.getPublic());
      methodSET.invoke(certInfoObject, getSetField(certInfoObject, "KEY"),
          certificateX509KeyObject);
      // info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
      Class<?> certificateVersionClass = Class.forName(getCertificateVersionModuleName());
      Constructor<?> certificateVersionConstr = certificateVersionClass.getConstructor(int.class);
      Constructor<?> certificateVersionConstr0 = certificateVersionClass.getConstructor();
      Object certInfoObject0 = certificateVersionConstr0.newInstance();
      Field v3IntField = certInfoObject0.getClass().getDeclaredField("V3");
      v3IntField.setAccessible(true);
      int fValue = v3IntField.getInt(certInfoObject0);
      Object certificateVersionObject = certificateVersionConstr.newInstance(fValue);
      methodSET.invoke(certInfoObject, getSetField(certInfoObject, "VERSION"),
          certificateVersionObject);

      // AlgorithmId algo = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);
      Class<?> algorithmIdClass = Class.forName(getAlgorithmIdModuleName());
      Field md5WithRSAField = algorithmIdClass.getDeclaredField("RSAEncryption_oid");
      md5WithRSAField.setAccessible(true);
      Class<?> objectIdentifierClass = Class.forName(getObjectIdentifierModuleName());

      Object md5WithRSAValue = md5WithRSAField.get(algorithmIdClass);

      Constructor<?> algorithmIdConstr = algorithmIdClass.getConstructor(objectIdentifierClass);
      Object algorithmIdObject = algorithmIdConstr.newInstance(md5WithRSAValue);

      // info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));
      Class<?> certificateAlgorithmIdClass = Class.forName(getCertificateAlgorithmIdModuleName());
      Constructor<?> certificateAlgorithmIdConstr = certificateAlgorithmIdClass
                                                        .getConstructor(algorithmIdClass);
      Object certificateAlgorithmIdObject = certificateAlgorithmIdConstr
                                                .newInstance(algorithmIdObject);
      methodSET.invoke(certInfoObject, getSetField(certInfoObject, "ALGORITHM_ID"),
          certificateAlgorithmIdObject);

      // Set the SAN extension
      Class<?> generalNameInterfaceClass = Class.forName(getGeneralNameInterfaceModuleName());

      Class<?> generalNameClass = Class.forName(getGeneralNameModuleName());
      Constructor<?> generalNameConstr = generalNameClass.getConstructor(generalNameInterfaceClass);

      // GeneralNames generalNames = new GeneralNames();
      Class<?> generalNamesClass = Class.forName(getGeneralNamesModuleName());
      Constructor<?> generalNamesConstr = generalNamesClass.getConstructor();
      Object generalNamesObject = generalNamesConstr.newInstance();
      Method generalNamesAdd = generalNamesObject.getClass().getMethod("add", generalNameClass);

      Class<?> dnsNameClass = Class.forName(getDNSNameModuleName());
      Constructor<?> dnsNameConstr = dnsNameClass.getConstructor(String.class);

      boolean generalNameAdded = false;
      // Pull the hostname out of the DN
      String hostname = dn.split(",", 2)[0].split("=", 2)[1];
      if("localhost".equals(hostname)) {
        // Add short hostname
        String detectedHostname = InetAddress.getLocalHost().getHostName();
        if (Character.isAlphabetic(detectedHostname.charAt(0))) {
          // DNSName dnsName = new DNSName(detectedHostname);
          Object dnsNameObject = dnsNameConstr.newInstance(detectedHostname);
          // GeneralName generalName = new GeneralName(dnsName);
          Object generalNameObject = generalNameConstr.newInstance(dnsNameObject);
          // generalNames.add(generalName);
          generalNamesAdd.invoke(generalNamesObject, generalNameObject);
          generalNameAdded = true;
        }

        // Add fully qualified hostname
        String detectedFullyQualifiedHostname = InetAddress.getLocalHost().getCanonicalHostName();
        if (Character.isAlphabetic(detectedFullyQualifiedHostname.charAt(0))) {
          // DNSName dnsName = new DNSName(detectedFullyQualifiedHostname);
          Object fullyQualifiedDnsNameObject = dnsNameConstr.newInstance(detectedFullyQualifiedHostname);
          // GeneralName generalName = new GeneralName(fullyQualifiedDnsNameObject);
          Object fullyQualifiedGeneralNameObject = generalNameConstr.newInstance(fullyQualifiedDnsNameObject);
          // generalNames.add(fullyQualifiedGeneralNameObject);
          generalNamesAdd.invoke(generalNamesObject, fullyQualifiedGeneralNameObject);
          generalNameAdded = true;
        }
      }

      if (Character.isAlphabetic(hostname.charAt(0))) {
        // DNSName dnsName = new DNSName(hostname);
        Object dnsNameObject = dnsNameConstr.newInstance(hostname);
        // GeneralName generalName = new GeneralName(dnsName);
        Object generalNameObject = generalNameConstr.newInstance(dnsNameObject);
        // generalNames.add(generalName);
        generalNamesAdd.invoke(generalNamesObject, generalNameObject);
        generalNameAdded = true;
      }

      if (generalNameAdded) {
        // SubjectAlternativeNameExtension san = new SubjectAlternativeNameExtension(generalNames);
        Class<?> subjectAlternativeNameExtensionClass = Class.forName(getSubjectAlternativeNameExtensionModuleName());
        Constructor<?> subjectAlternativeNameExtensionConstr = subjectAlternativeNameExtensionClass.getConstructor(generalNamesClass);
        Object subjectAlternativeNameExtensionObject = subjectAlternativeNameExtensionConstr.newInstance(generalNamesObject);

        // CertificateExtensions certificateExtensions = new CertificateExtensions();
        Class<?> certificateExtensionsClass = Class.forName(getCertificateExtensionsModuleName());
        Constructor<?> certificateExtensionsConstr = certificateExtensionsClass.getConstructor();
        Object certificateExtensionsObject = certificateExtensionsConstr.newInstance();

        // certificateExtensions.set(san.getExtensionId().toString(), san);
        Method getExtensionIdMethod = subjectAlternativeNameExtensionObject.getClass().getMethod("getExtensionId");
        String sanExtensionId = getExtensionIdMethod.invoke(subjectAlternativeNameExtensionObject).toString();
        Method certificateExtensionsSet = certificateExtensionsObject.getClass().getMethod("set", String.class, Object.class);
        certificateExtensionsSet.invoke(certificateExtensionsObject, sanExtensionId, subjectAlternativeNameExtensionObject);

        // info.set(X509CertInfo.EXTENSIONS, certificateExtensions);
        methodSET.invoke(certInfoObject, getSetField(certInfoObject, "EXTENSIONS"), certificateExtensionsObject);
      }

      // Sign the cert to identify the algorithm that's used.
      // X509CertImpl cert = new X509CertImpl(info);
      Class<?> x509CertImplClass = Class.forName(getX509CertImplModuleName());
      Constructor<?> x509CertImplConstr = x509CertImplClass.getConstructor(certInfoClass);
      x509CertImplObject = x509CertImplConstr.newInstance(certInfoObject);

      // cert.sign(privkey, algorithm);
      Method methoSIGN = x509CertImplObject.getClass().getMethod("sign",
          PrivateKey.class, String.class);
      methoSIGN.invoke(x509CertImplObject, privkey, algorithm);

      // Update the algorith, and resign.
      // algo = (AlgorithmId)cert.get(X509CertImpl.SIG_ALG);
      Method methoGET = x509CertImplObject.getClass().getMethod("get", String.class);
      String sig_alg = getSetField(x509CertImplObject, "SIG_ALG");

      String certAlgoIdNameValue = getSetField(certificateAlgorithmIdObject, "NAME");
      String certAlgoIdAlgoValue = getSetField(certificateAlgorithmIdObject, "ALGORITHM");
      // info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
      methodSET.invoke(certInfoObject, certAlgoIdNameValue + "." + certAlgoIdAlgoValue,
          methoGET.invoke(x509CertImplObject, sig_alg));

      // cert = new X509CertImpl(info);
      x509CertImplObject = x509CertImplConstr.newInstance(certInfoObject);
      // cert.sign(privkey, algorithm);
      methoSIGN.invoke(x509CertImplObject, privkey, algorithm);
    } catch (Exception e) {
      LOG.failedToGenerateCertificate(e);
    }
    return (X509Certificate) x509CertImplObject;
  }

  private static String getX509CertInfoModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.X509CertInfo"
               : "sun.security.x509.X509CertInfo";
  }

  private static String getX509CertifValidityModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.CertificateValidity" :
               "sun.security.x509.CertificateValidity";
  }

  private static String getX509X500NameModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.X500Name" :
               "sun.security.x509.X500Name";
  }

  private static String getCertificateSerialNumberModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.CertificateSerialNumber" :
               "sun.security.x509.CertificateSerialNumber";
  }

  private static String getCertificateSubjectNameModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.CertificateSubjectName" :
               "sun.security.x509.CertificateSubjectName";
  }

  private static String getCertificateIssuerNameModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.CertificateIssuerName" :
               "sun.security.x509.CertificateIssuerName";
  }

  private static String getCertificateX509KeyModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.CertificateX509Key" :
               "sun.security.x509.CertificateX509Key";
  }

  private static String getCertificateVersionModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.CertificateVersion" :
               "sun.security.x509.CertificateVersion";
  }

  private static String getAlgorithmIdModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.AlgorithmId" :
               "sun.security.x509.AlgorithmId";
  }

  private static String getObjectIdentifierModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.util.ObjectIdentifier" :
               "sun.security.util.ObjectIdentifier";
  }

  private static String getCertificateAlgorithmIdModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.CertificateAlgorithmId" :
               "sun.security.x509.CertificateAlgorithmId";
  }

  private static String getGeneralNameInterfaceModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.GeneralNameInterface" :// TODO
               "sun.security.x509.GeneralNameInterface";
  }

  private static String getGeneralNameModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.GeneralName" : // TODO
               "sun.security.x509.GeneralName";
  }

  private static String getGeneralNamesModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.GeneralNames" : // TODO
               "sun.security.x509.GeneralNames";
  }

  private static String getDNSNameModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.DNSName" : // TODO
               "sun.security.x509.DNSName";
  }

  private static String getSubjectAlternativeNameExtensionModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.SubjectAlternativeNameExtension" : // TODO
               "sun.security.x509.SubjectAlternativeNameExtension";
  }

  private static String getCertificateExtensionsModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.CertificateExtensions" : // TODO
               "sun.security.x509.CertificateExtensions";
  }

  private static String getX509CertImplModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ?
               "com.ibm.security.x509.X509CertImpl" :
               "sun.security.x509.X509CertImpl";
  }

  private static String getSetField(Object obj, String setString)
      throws Exception {
    Field privateStringField = obj.getClass().getDeclaredField(setString);
    privateStringField.setAccessible(true);
    return (String) privateStringField.get(obj);
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
      return x509Certificate.getSubjectDN().equals(x509Certificate.getIssuerDN());
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
