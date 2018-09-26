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
package org.apache.knox.gateway.services.security.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;

public class X509CertificateUtil {

  private static GatewaySpiMessages LOG = MessagesFactory.get(GatewaySpiMessages.class);

  /**
   * Create a self-signed X.509 Certificate
   * @param dn the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
   * @param pair the KeyPair
   * @param days how many days from now the Certificate is valid for
   * @param algorithm the signing algorithm, eg "SHA1withRSA"
   */
  public static X509Certificate generateCertificate(String dn, KeyPair pair,
   int days, String algorithm) throws GeneralSecurityException, IOException {

  PrivateKey privkey = pair.getPrivate();
  Object x509CertImplObject = null;
  try {
    Date from = new Date();
    Date to = new Date(from.getTime() + days * 86400000l);

    Class<?> certInfoClass = Class.forName(getX509CertInfoModuleName());
    Constructor<?> certInfoConstr = certInfoClass.getConstructor();
    Object certInfoObject = certInfoConstr.newInstance();

    // CertificateValidity interval = new CertificateValidity(from, to);
    Class<?> certValidityClass = Class.forName(getX509CertifValidityModuleName());
    Constructor<?> certValidityConstr = certValidityClass
        .getConstructor(new Class[] { Date.class, Date.class });
    Object certValidityObject = certValidityConstr.newInstance(from, to);

    BigInteger sn = new BigInteger(64, new SecureRandom());

    // X500Name owner = new X500Name(dn);
    Class<?> x500NameClass = Class.forName(getX509X500NameModuleName());
    Constructor<?> x500NameConstr = x500NameClass
        .getConstructor(new Class[] { String.class });
    Object x500NameObject = x500NameConstr.newInstance(dn);

    Method methodSET = certInfoObject.getClass().getMethod("set", String.class, Object.class);

    // info.set(X509CertInfo.VALIDITY, interval);
    methodSET.invoke(certInfoObject, getSetField(certInfoObject, "VALIDITY"),certValidityObject);

    // info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
    Class<?> certificateSerialNumberClass = Class.forName(getCertificateSerialNumberModuleName());
    Constructor<?> certificateSerialNumberConstr = certificateSerialNumberClass
        .getConstructor(new Class[] { BigInteger.class });
    Object certificateSerialNumberObject = certificateSerialNumberConstr
        .newInstance(sn);
    methodSET.invoke(certInfoObject, getSetField(certInfoObject, "SERIAL_NUMBER"),
        certificateSerialNumberObject);

    // info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
    try {
      Class<?> certificateSubjectNameClass = Class.forName(getCertificateSubjectNameModuleName());
      Constructor<?> certificateSubjectNameConstr = certificateSubjectNameClass
          .getConstructor(new Class[] { x500NameClass });
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
          .getConstructor(new Class[] { x500NameClass });
      Object certificateIssuerNameObject = certificateIssuerNameConstr
          .newInstance(x500NameObject);
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
        .getConstructor(new Class[] { PublicKey.class });
    Object certificateX509KeyObject = certificateX509KeyConstr
        .newInstance(pair.getPublic());
    methodSET.invoke(certInfoObject, getSetField(certInfoObject, "KEY"),
        certificateX509KeyObject);
    // info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
    Class<?> certificateVersionClass = Class.forName(getCertificateVersionModuleName());
    Constructor<?> certificateVersionConstr = certificateVersionClass
        .getConstructor(new Class[] { int.class });
    Constructor<?> certificateVersionConstr0 = certificateVersionClass
        .getConstructor();
    Object certInfoObject0 = certificateVersionConstr0.newInstance();
    Field v3IntField = certInfoObject0.getClass()
        .getDeclaredField("V3");
    v3IntField.setAccessible(true);
    int fValue = (int) v3IntField.getInt(certInfoObject0);
    Object certificateVersionObject = certificateVersionConstr
        .newInstance(fValue);
    methodSET.invoke(certInfoObject, getSetField(certInfoObject, "VERSION"),
        certificateVersionObject);

    // AlgorithmId algo = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);
    Class<?> algorithmIdClass = Class.forName(getAlgorithmIdModuleName());
    Field md5WithRSAField = algorithmIdClass
        .getDeclaredField("md5WithRSAEncryption_oid");
    md5WithRSAField.setAccessible(true);
    Class<?> objectIdentifierClass = Class.forName(getObjectIdentifierModuleName());

    Object md5WithRSAValue = md5WithRSAField.get(algorithmIdClass);

    Constructor<?> algorithmIdConstr = algorithmIdClass
        .getConstructor(new Class[] { objectIdentifierClass });
    Object algorithmIdObject = algorithmIdConstr.newInstance(md5WithRSAValue);

    // info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));
    Class<?> certificateAlgorithmIdClass = Class.forName(getCertificateAlgorithmIdModuleName());
    Constructor<?> certificateAlgorithmIdConstr = certificateAlgorithmIdClass
        .getConstructor(new Class[] { algorithmIdClass });
    Object certificateAlgorithmIdObject = certificateAlgorithmIdConstr
        .newInstance(algorithmIdObject);
    methodSET.invoke(certInfoObject, getSetField(certInfoObject, "ALGORITHM_ID"),
        certificateAlgorithmIdObject);

    // Sign the cert to identify the algorithm that's used.
    // X509CertImpl cert = new X509CertImpl(info);
    Class<?> x509CertImplClass = Class.forName(getX509CertImplModuleName());
    Constructor<?> x509CertImplConstr = x509CertImplClass
        .getConstructor(new Class[] { certInfoClass });
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
    methodSET.invoke(certInfoObject, certAlgoIdNameValue + "."
        + certAlgoIdAlgoValue,
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
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.CertificateValidity"
        : "sun.security.x509.CertificateValidity";
  }

  private static String getX509X500NameModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.X500Name"
        : "sun.security.x509.X500Name";
  }

  private static String getCertificateSerialNumberModuleName() {
   return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.CertificateSerialNumber"
        : "sun.security.x509.CertificateSerialNumber";
  }

  private static String getCertificateSubjectNameModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.CertificateSubjectName"
        : "sun.security.x509.CertificateSubjectName";
  }

  private static String getCertificateIssuerNameModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.CertificateIssuerName"
        : "sun.security.x509.CertificateIssuerName";
  }

  private static String getCertificateX509KeyModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.CertificateX509Key"
        : "sun.security.x509.CertificateX509Key";
  }

  private static String getCertificateVersionModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.CertificateVersion"
        : "sun.security.x509.CertificateVersion";
  }

  private static String getAlgorithmIdModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.AlgorithmId"
        : "sun.security.x509.AlgorithmId";
  }

  private static String getObjectIdentifierModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.util.ObjectIdentifier"
        : "sun.security.util.ObjectIdentifier";
  }

  private static String getCertificateAlgorithmIdModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.CertificateAlgorithmId"
        : "sun.security.x509.CertificateAlgorithmId";
  }

  private static String getX509CertImplModuleName() {
    return System.getProperty("java.vendor").contains("IBM") ? "com.ibm.security.x509.X509CertImpl"
        : "sun.security.x509.X509CertImpl";
  }

  private static String getSetField(Object obj, String setString)
      throws Exception {
    Field privateStringField = obj.getClass().getDeclaredField(setString);
    privateStringField.setAccessible(true);
    String fieldValue = (String) privateStringField.get(obj);
    return fieldValue;
  }

  public static void writeCertificateToFile(Certificate cert, final File file)
       throws CertificateEncodingException, IOException {
    byte[] bytes = cert.getEncoded();
    Base64 encoder = new Base64( 76, "\n".getBytes( "ASCII" ) );
    try( final FileOutputStream out = new FileOutputStream( file ) ) {
      out.write( "-----BEGIN CERTIFICATE-----\n".getBytes( "ASCII" ) );
      out.write( encoder.encodeToString( bytes ).getBytes( "ASCII" ) );
      out.write( "-----END CERTIFICATE-----\n".getBytes( "ASCII" ) );
    }
  }

  public static void writeCertificateToJKS(Certificate cert, final File file)
      throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

    char[] password = "changeit".toCharArray();
    ks.load(null, password);
    ks.setCertificateEntry("gateway-identity", cert);
    FileOutputStream fos = new FileOutputStream(file);
    /* Coverity Scan CID 1361992 */
    try {
      ks.store(fos, password);
    } finally {
      fos.close();
    }
  }
}

