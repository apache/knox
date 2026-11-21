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
409 path is genuinely exercised. The end-to-end case then exchanges the minted
``client_secret`` for an access token through the ``client_credentials`` grant and
presents that to the JWTProvider-federated ``knoxtoken`` topology, to prove a custom
id authenticates like any other.

KNOX-3601: a client secret is a grant credential, not an access token. The
JWTProvider rejects it when presented directly (``Bearer``, or HTTP Basic with the
``Passcode`` username) unless the topology sets
``knox.token.allow.client.secret.as.access.token``:

* ``knoxtoken`` — default: a client secret used as an access token is rejected
  (401), while an API key and a ``client_credentials`` exchange still work.
* ``knoxtoken-allow-client-secret`` — opted in: the client secret is accepted.
"""

import base64
import re
import unittest
import uuid

from requests.auth import HTTPBasicAuth

from common_utils import (
    gateway_base_url,
    get_token_claim,
    knox_delete,
    knox_get,
    knox_post,
)

UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)

# JWTFederationFilter.CLIENT_SECRET_AS_ACCESS_TOKEN_ERROR
CLIENT_SECRET_AS_ACCESS_TOKEN_ERROR = (
    "A client secret is not an access token; "
    "exchange it for one using the client_credentials grant"
)


class TestClientIdCredentials(unittest.TestCase):  # pylint: disable=too-many-instance-attributes
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
        # Same, opted in to accepting a client secret as an access token.
        self.allow_client_secret_pre_url = (
            self.base_url + "gateway/knoxtoken-allow-client-secret/auth/api/v1/pre"
        )
        # OAuth token endpoint of the KNOXTOKEN service, behind the same JWTProvider.
        self.oauth_token_url = self.base_url + "gateway/knoxtoken/oauth/v1/token"
        # API keys are minted on behalf of a fresh user so the per-user limit there is never hit.
        self.api_key_url = self.base_url + "gateway/tokenlimitbytype/apikey/api/v1/auth/key"
        self.admin_auth = HTTPBasicAuth("admin", "admin-password")
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

    @staticmethod
    def _passcode_basic(secret):
        """An Authorization header presenting a passcode as HTTP Basic, username Passcode."""
        basic = base64.b64encode(("Passcode:" + secret).encode("utf-8")).decode("ascii")
        return {"Authorization": "Basic " + basic}

    @staticmethod
    def _bearer(token):
        return {"Authorization": "Bearer " + token}

    def _exchange_client_secret(self, client_id, client_secret, basic_auth):
        """Exchange client credentials for an access token via the client_credentials grant."""
        data = {"grant_type": "client_credentials"}
        if basic_auth:
            return knox_post(
                self.oauth_token_url,
                data=data,
                auth=HTTPBasicAuth(client_id, client_secret),
            )
        data.update({"client_id": client_id, "client_secret": client_secret})
        return knox_post(self.oauth_token_url, data=data)

    def _assert_authenticated_as(self, response, actor, what):
        self.assertEqual(
            response.status_code,
            200,
            msg=f"{what} was not accepted: {response.status_code} {response.text}",
        )
        self.assertEqual(response.headers.get("x-knox-actor-username"), actor)

    def test_supplied_client_id_authenticates(self):
        """A custom client_id's exchanged access token authenticates (E2E)."""
        supplied = "e2e-app_" + uuid.uuid4().hex
        payload = self._register_ok(self.enabled_url, client_id=supplied)

        exchange = self._exchange_client_secret(
            supplied, payload["client_secret"], basic_auth=True
        )
        self.assertEqual(
            exchange.status_code,
            200,
            msg=f"client_credentials grant failed: "
            f"{exchange.status_code} {exchange.text}",
        )
        access_token = exchange.json()["access_token"]
        # For a CLIENTID (third-party-app) token the authenticated principal is the token_id
        # itself, so the custom client_id becomes the subject of the exchanged access token...
        self.assertEqual(get_token_claim(access_token, "sub"), supplied)

        response = knox_get(self.federated_pre_url, headers=self._bearer(access_token))
        # ...and flows through as the actor username.
        self._assert_authenticated_as(response, supplied, "exchanged access token")

    def test_client_secret_exchanged_through_client_credentials_grant(self):
        """KNOX-3601: the grant still accepts a client secret, in the body or as HTTP Basic."""
        payload = self._register_ok(self.enabled_url)
        for basic_auth in (False, True):
            with self.subTest(basic_auth=basic_auth):
                response = self._exchange_client_secret(
                    payload["client_id"], payload["client_secret"], basic_auth
                )
                self.assertEqual(
                    response.status_code,
                    200,
                    msg=f"client_credentials grant failed: "
                    f"{response.status_code} {response.text}",
                )
                self.assertIn("access_token", response.json())

    def test_client_secret_rejected_as_access_token(self):
        """KNOX-3601: a client secret presented directly as an access token is rejected (401)."""
        payload = self._register_ok(self.enabled_url)
        secret = payload["client_secret"]
        for presented_as, headers in (
            ("Bearer", self._bearer(secret)),
            ("Basic Passcode", self._passcode_basic(secret)),
        ):
            with self.subTest(presented_as=presented_as):
                response = knox_get(self.federated_pre_url, headers=headers)
                self.assertEqual(
                    response.status_code,
                    401,
                    msg=f"client secret as {presented_as} access token was not rejected: "
                    f"{response.status_code} {response.text}",
                )
                self.assertIn(CLIENT_SECRET_AS_ACCESS_TOKEN_ERROR, response.text)
                self.assertIsNone(response.headers.get("x-knox-actor-username"))

    def test_client_secret_accepted_as_access_token_when_allowed(self):
        """KNOX-3601: a topology that opts in still accepts a client secret as an access token."""
        supplied = "optin-app_" + uuid.uuid4().hex
        payload = self._register_ok(self.enabled_url, client_id=supplied)
        secret = payload["client_secret"]
        for presented_as, headers in (
            ("Bearer", self._bearer(secret)),
            ("Basic Passcode", self._passcode_basic(secret)),
        ):
            with self.subTest(presented_as=presented_as):
                response = knox_get(self.allow_client_secret_pre_url, headers=headers)
                self._assert_authenticated_as(
                    response, supplied, f"client secret as {presented_as} access token"
                )

    def test_api_key_accepted_as_access_token(self):
        """KNOX-3601: API keys share the passcode format but are not client secrets."""
        user = "apikey-" + uuid.uuid4().hex[:12]
        minted = knox_post(self.api_key_url, auth=self.admin_auth, params={"doAs": user})
        self.assertEqual(
            minted.status_code,
            200,
            msg=f"API key was not issued: {minted.status_code} {minted.text}",
        )
        api_key = minted.json()["api_key"]
        for presented_as, headers in (
            ("Bearer", self._bearer(api_key)),
            ("Basic Passcode", self._passcode_basic(api_key)),
        ):
            with self.subTest(presented_as=presented_as):
                response = knox_get(self.federated_pre_url, headers=headers)
                self._assert_authenticated_as(
                    response, user, f"API key as {presented_as} access token"
                )

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
