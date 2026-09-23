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

"""End-to-end tests for the Client Credentials endpoint's user-supplied clientId.

The CLIENTID service (an extension of KNOXTOKEN) exposes
``clientid/api/v1/oauth/credentials`` and returns ``{"client_id", "client_secret"}``
where ``client_id`` is the token's ``token_id`` primary key and ``client_secret``
is a passcode. Historically ``client_id`` was always a server-generated UUID.

These tests cover the ``clientid.allowUserSuppliedClientId`` feature via two
topologies bind-mounted into the CI gateway:

* ``clientid`` — feature ON (``clientid.allowUserSuppliedClientId=true``): a
  caller-supplied ``clientId`` query param becomes the ``client_id``/``token_id``.
* ``clientid-default`` — feature OFF (param unset): a supplied ``clientId`` is
  ignored and a UUID is generated (proves the default is non-disruptive).

The gateway runs on H2 (persistent, primary-key enforced), so the duplicate ->
409 path is genuinely exercised. The end-to-end case then presents the minted
``client_secret`` as a Passcode to the JWTProvider-federated ``knoxtoken``
topology to prove a custom id authenticates like any other.
"""

import base64
import re
import unittest
import uuid

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_delete, knox_get, knox_post

UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)


class TestClientIdCredentials(unittest.TestCase):
    """Register client credentials with and without a caller-supplied clientId."""

    def setUp(self):
        self.base_url = gateway_base_url()
        # Feature ON: honor a caller-supplied clientId as the token_id.
        self.enabled_url = (
            self.base_url + "gateway/clientid/clientid/api/v1/oauth/credentials"
        )
        # Feature OFF (default): a supplied clientId must be ignored.
        self.default_url = (
            self.base_url
            + "gateway/clientid-default/clientid/api/v1/oauth/credentials"
        )
        # JWTProvider-protected auth service used to verify a minted passcode.
        self.federated_pre_url = self.base_url + "gateway/knoxtoken/auth/api/v1/pre"
        # v2 revoke lives on the KNOXTOKEN service in the knoxldap topology.
        self.token_v2_url = self.base_url + "gateway/knoxldap/knoxtoken/api/v2/token"

        self.guest_auth = HTTPBasicAuth("guest", "guest-password")
        self._minted_client_ids = []

    def tearDown(self):
        for client_id in self._minted_client_ids:
            self._revoke_quietly(client_id)

    def _revoke_quietly(self, client_id):
        """Best-effort revoke of a minted client_id (= token_id).

        A generated UUID id is revocable via the v2 revoke endpoint; a custom
        (non-UUID) id is not resolvable by ``TokenUtils.getTokenId`` and simply
        fails here. The CI gateway is fresh per run and ids are unique, so any
        failure is harmless — swallow everything.
        """
        try:
            knox_delete(
                self.token_v2_url + "/revoke",
                data=client_id,
                auth=self.guest_auth,
            )
        except Exception:  # pylint: disable=broad-except
            pass

    def _register(self, url, client_id=None):
        """POST to a credentials endpoint, optionally supplying a clientId param."""
        params = {"clientId": client_id} if client_id is not None else None
        return knox_post(url, auth=self.guest_auth, params=params)

    def _register_ok(self, url, client_id=None):
        """Register credentials, assert 200, track the id, and return the body."""
        response = self._register(url, client_id=client_id)
        self.assertEqual(
            response.status_code,
            200,
            msg=f"Registration failed: {response.status_code} {response.text}",
        )
        payload = response.json()
        self.assertIn("client_id", payload)
        self.assertIn("client_secret", payload)
        self._minted_client_ids.append(payload["client_id"])
        return payload

    def test_supplied_client_id_is_used(self):
        """An enabled topology returns the caller-supplied clientId as client_id."""
        supplied = "my-app.dev_" + uuid.uuid4().hex
        payload = self._register_ok(self.enabled_url, client_id=supplied)
        self.assertEqual(payload["client_id"], supplied)

    def test_supplied_client_id_authenticates(self):
        """A custom client_id's client_secret authenticates as a Passcode (E2E)."""
        supplied = "e2e-app_" + uuid.uuid4().hex
        payload = self._register_ok(self.enabled_url, client_id=supplied)

        basic = base64.b64encode(
            ("Passcode:" + payload["client_secret"]).encode("utf-8")
        ).decode("ascii")
        response = knox_get(
            self.federated_pre_url,
            headers={"Authorization": "Basic " + basic},
        )
        self.assertEqual(
            response.status_code,
            200,
            msg=f"custom-id passcode was not accepted: "
            f"{response.status_code} {response.text}",
        )
        # For a CLIENTID (third-party-app) token the authenticated principal's audit username is
        # the token_id itself, so the custom client_id flows through as the actor username.
        self.assertEqual(response.headers.get("x-knox-actor-username"), supplied)

    def test_omitted_client_id_returns_generated_id(self):
        """With no clientId param, an enabled topology still generates a UUID."""
        payload = self._register_ok(self.enabled_url)
        self.assertRegex(payload["client_id"], UUID_RE)

    def test_invalid_client_id_rejected(self):
        """A present-but-malformed clientId is a client error (400)."""
        response = self._register(self.enabled_url, client_id="bad id/with*chars")
        self.assertEqual(
            response.status_code,
            400,
            msg=f"expected 400 for invalid clientId, got "
            f"{response.status_code}: {response.text}",
        )
        self.assertEqual(response.json().get("error"), "invalid_request")

    def test_too_long_client_id_rejected(self):
        """A clientId longer than the 128-char column width is rejected (400)."""
        response = self._register(self.enabled_url, client_id="a" * 129)
        self.assertEqual(
            response.status_code,
            400,
            msg=f"expected 400 for over-long clientId, got "
            f"{response.status_code}: {response.text}",
        )
        self.assertEqual(response.json().get("error"), "invalid_request")

    def test_duplicate_client_id_conflict(self):
        """Registering the same clientId twice yields 409 on the second call."""
        supplied = "dup-app_" + uuid.uuid4().hex

        first = self._register_ok(self.enabled_url, client_id=supplied)
        self.assertEqual(first["client_id"], supplied)

        second = self._register(self.enabled_url, client_id=supplied)
        self.assertEqual(
            second.status_code,
            409,
            msg=f"expected 409 for duplicate clientId, got "
            f"{second.status_code}: {second.text}",
        )
        self.assertEqual(second.json().get("error"), "invalid_client")

    def test_supplied_client_id_ignored_when_disabled(self):
        """The default topology ignores a supplied clientId and generates a UUID."""
        supplied = "ignored-app_" + uuid.uuid4().hex
        payload = self._register_ok(self.default_url, client_id=supplied)
        self.assertNotEqual(payload["client_id"], supplied)
        self.assertRegex(payload["client_id"], UUID_RE)


if __name__ == "__main__":
    unittest.main()
