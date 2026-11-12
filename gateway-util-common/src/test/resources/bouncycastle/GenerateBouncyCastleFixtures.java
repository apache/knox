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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

/**
 * Generates the BouncyCastle backward-compatibility fixtures read by BouncyCastleCompatibilityTest.
 * Not compiled by the build. Before bumping BouncyCastle, run it against the CURRENT (outgoing)
 * BouncyCastle jars, so the new version is tested against files the old version wrote:
 *
 *   BC=~/.m2/repository/org/bouncycastle; V=1.85
 *   java -cp $BC/bcprov-jdk18on/$V/bcprov-jdk18on-$V.jar:$BC/bcpkix-jdk18on/$V/bcpkix-jdk18on-$V.jar:$BC/bcutil-jdk18on/$V/bcutil-jdk18on-$V.jar \
 *     GenerateBouncyCastleFixtures.java $V gateway-util-common/src/test/resources/bouncycastle
 *
 * then add the version to BouncyCastleCompatibilityTest.FIXTURE_VERSIONS.
 */
public class GenerateBouncyCastleFixtures {
  static final String PASSWORD = "knox-bc-fixture";

  public static void main(String[] args) throws Exception {
    String version = args[0];
    Path dir = Paths.get(args[1]);
    Security.addProvider(new BouncyCastleProvider());

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair pair = kpg.generateKeyPair();
    X500Name dn = new X500Name("CN=bc-fixture-" + version + ",OU=Test,O=Hadoop,L=Test,ST=Test,C=US");
    Date from = new Date();
    Date to = new Date(from.getTime() + 36500L * 86400000L); // fixtures must not expire
    X509Certificate cert = new JcaX509CertificateConverter().getCertificate(
        new JcaX509v3CertificateBuilder(dn, BigInteger.valueOf(System.currentTimeMillis()), from, to, dn, pair.getPublic())
            .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));

    KeyStore ks = KeyStore.getInstance("BCFKS", BouncyCastleProvider.PROVIDER_NAME);
    ks.load(null, null);
    ks.setKeyEntry("gateway-identity", pair.getPrivate(), PASSWORD.toCharArray(), new Certificate[] {cert});
    ks.setCertificateEntry("trusted-cert", cert);
    try (OutputStream out = Files.newOutputStream(dir.resolve("bc-" + version + ".bcfks"))) {
      ks.store(out, PASSWORD.toCharArray());
    }

    String pem = "-----BEGIN CERTIFICATE-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(cert.getEncoded())
        + "\n-----END CERTIFICATE-----\n";
    Files.write(dir.resolve("bc-" + version + ".pem"), pem.getBytes(StandardCharsets.US_ASCII));
  }
}
