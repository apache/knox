# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Integration checks for the gateway's self-signed TLS identity certificate.

The image ships no identity keystore, so on first start the gateway builds its
own certificate with BouncyCastle (DefaultKeystoreService ->
X509CertificateUtil.generateCertificate). Every other suite talks to the gateway
with verify=False, so a certificate BouncyCastle builds wrongly but that still
loads would not fail them (KNOX-3566). This suite inspects that certificate and
lets OpenSSL, a TLS stack independent of the JVM, validate it in a strict
handshake.
"""

import datetime
import socket
import ssl
import unittest

from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtensionOID, SignatureAlgorithmOID

from common_utils import (
    KNOX_REQUEST_TIMEOUT,
    gateway_certificate,
    gateway_certificate_pem,
    gateway_host_port,
)

# DefaultKeystoreService's DN template with the default 'localhost' cert gen mode.
# BouncyCastle encodes the RDNs in template order; RFC 4514 renders them reversed.
EXPECTED_SUBJECT = "C=US,ST=Test,L=Test,O=Hadoop,OU=Test,CN=localhost"
# The SAN always carries the CN. The container hostname is a hex id, which is
# only added when it starts with a letter, so the suite cannot rely on 'knox'.
CERT_HOSTNAME = "localhost"
VALIDITY = datetime.timedelta(days=365)


class TestGatewayIdentityCertificate(unittest.TestCase):
    """Checks the certificate the gateway generated for itself with BouncyCastle."""

    @classmethod
    def setUpClass(cls):
        cls.cert = gateway_certificate()

    def test_self_signed_and_signature_verifies(self):
        """Issuer equals subject and the signature verifies with the certificate's own key."""
        self.assertEqual(self.cert.issuer, self.cert.subject)
        self.cert.verify_directly_issued_by(self.cert)

    def test_key_and_signature_algorithm(self):
        """RSA 2048 key signed with SHA256withRSA, as an X.509 v3 certificate."""
        self.assertEqual(self.cert.version, x509.Version.v3)
        public_key = self.cert.public_key()
        self.assertIsInstance(public_key, rsa.RSAPublicKey)
        self.assertEqual(public_key.key_size, 2048)
        self.assertEqual(self.cert.signature_algorithm_oid, SignatureAlgorithmOID.RSA_WITH_SHA256)
        self.assertIsInstance(self.cert.signature_hash_algorithm, hashes.SHA256)

    def test_validity(self):
        """Currently valid, for exactly the 365 days the gateway asks for."""
        now = datetime.datetime.now(datetime.timezone.utc)
        self.assertLessEqual(self.cert.not_valid_before_utc, now)
        self.assertLess(now, self.cert.not_valid_after_utc)
        self.assertEqual(self.cert.not_valid_after_utc - self.cert.not_valid_before_utc, VALIDITY)

    def test_subject_and_subject_alternative_name(self):
        """Subject follows the gateway DN template and the SAN carries the CN hostname."""
        self.assertEqual(self.cert.subject.rfc4514_string(), EXPECTED_SUBJECT)
        san = self.cert.extensions.get_extension_for_class(x509.SubjectAlternativeName)
        self.assertFalse(san.critical)
        self.assertIn(CERT_HOSTNAME, san.value.get_values_for_type(x509.DNSName))

    def test_no_extended_key_usage(self):
        """Without single-EKU mode the generated identity carries no EKU extension."""
        with self.assertRaises(x509.ExtensionNotFound):
            self.cert.extensions.get_extension_for_oid(ExtensionOID.EXTENDED_KEY_USAGE)

    def test_strict_tls_handshake_trusting_only_the_identity(self):
        """OpenSSL completes a verified handshake (chain and hostname) trusting only this cert."""
        context = ssl.create_default_context(cadata=gateway_certificate_pem())
        # Not the default before Python 3.13; RFC 5280 strictness would also demand
        # extensions the gateway does not add (authority/subject key identifiers).
        context.verify_flags &= ~ssl.VERIFY_X509_STRICT
        with socket.create_connection(gateway_host_port(), timeout=KNOX_REQUEST_TIMEOUT) as sock:
            with context.wrap_socket(sock, server_hostname=CERT_HOSTNAME) as tls:
                self.assertIn(tls.version(), ("TLSv1.2", "TLSv1.3"))


if __name__ == "__main__":
    unittest.main()
