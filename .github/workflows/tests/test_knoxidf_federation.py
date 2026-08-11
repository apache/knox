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

"""End-to-end test of the KnoxIDF federation path: Knox brokering login to Keycloak.

This is opt-in and NOT part of the default test run (the base docker-compose ignores it).
It runs only under the docker-compose.knoxidf-federation.yml override, which stands up a
real Keycloak as the external OpenID Provider. See that override file for how to run it.

Flow exercised (all hops carried on one requests.Session so cookies persist):
  1. register an OIDC client on the knoxidf-sso topology (anonymous registration),
  2. GET /authorize -> SSOCookieProvider redirects to knoxsso with a federated-OP session id,
  3. GET /api/v1/websso/federated/op -> redirect to Keycloak's authorize endpoint,
  4. submit alice's credentials to Keycloak's login form -> redirect to the Knox callback,
  5. Knox validates the OP id_token, resumes /authorize, and redirects with a Knox code,
  6. exchange the Knox code at the token endpoint for Knox access/id/refresh tokens.
"""

import os
import unittest
import uuid
from html.parser import HTMLParser
from urllib.parse import urljoin, urlparse, parse_qs

import requests

from common_utils import gateway_base_url, get_token_claim, KNOX_REQUEST_TIMEOUT

# Client-side (relying-party) details. The redirect_uri is a loopback http URL, which the
# registration redirect-URI policy permits (RFC 8252). The client state is echoed back to
# the client redirect and is distinct from the federated-OP login session id.
CLIENT_REDIRECT_URI = "http://localhost/callback"
CLIENT_STATE = "knox_fed_client_state"

# The seeded Keycloak realm user (see compose/keycloak/realm.json).
KC_USERNAME = "alice"
KC_PASSWORD = "alice-password"
KC_EMAIL = "alice@example.com"


class _LoginFormParser(HTMLParser):
    """Scrapes the first HTML <form>: its action plus every input's name/value."""

    def __init__(self):
        super().__init__()
        self.action = None
        self.inputs = {}
        self._in_form = False

    def handle_starttag(self, tag, attrs):
        """Record the form action and any inputs inside the first form."""
        attributes = dict(attrs)
        if tag == "form" and self.action is None:
            self.action = attributes.get("action")
            self._in_form = True
        elif tag == "input" and self._in_form:
            name = attributes.get("name")
            if name:
                self.inputs[name] = attributes.get("value", "")

    def handle_endtag(self, tag):
        """Stop collecting inputs once the first form closes."""
        if tag == "form":
            self._in_form = False


class TestKnoxIDFFederation(unittest.TestCase):
    """Federation broker tests: Knox delegating authentication to Keycloak."""

    def setUp(self):
        self.base_url = gateway_base_url()
        self.knoxidf_sso_url = f"{self.base_url}gateway/knoxidf-sso/"
        self.knoxsso_url = f"{self.base_url}gateway/knoxsso/"
        self.knoxidf_token_url = f"{self.base_url}gateway/knoxidf-token/"
        self.keycloak_url = os.environ.get("KEYCLOAK_URL", "http://keycloak:8080")

    @staticmethod
    def _new_session():
        """A cookie-carrying session that tolerates Knox's self-signed TLS."""
        session = requests.Session()
        session.verify = False
        return session

    def _register_client(self, session):
        """Register a confidential client and return (client_id, client_secret)."""
        url = f"{self.knoxidf_sso_url}knoxidf/api/v1/client/register"
        payload = {
            "redirect_uris": CLIENT_REDIRECT_URI,
            "allowed_scopes": "openid,profile,email,offline_access",
        }
        response = session.post(url, data=payload, timeout=KNOX_REQUEST_TIMEOUT)
        self.assertEqual(response.status_code, 200, response.text)
        body = response.json()
        return body["client_id"], body["client_secret"]

    def _start_authorize(self, session, client_id):
        """Kick off /authorize; return the federated-OP login session id from the redirect."""
        url = f"{self.knoxidf_sso_url}knoxidf/api/v1/authorize"
        params = {
            "response_type": "code",
            "client_id": client_id,
            "redirect_uri": CLIENT_REDIRECT_URI,
            "scope": "openid offline_access",
            "state": CLIENT_STATE,
        }
        response = session.get(url, params=params, allow_redirects=False,
                               timeout=KNOX_REQUEST_TIMEOUT)
        self.assertIn(response.status_code, (302, 303, 307), response.text)
        location = response.headers.get("Location")
        self.assertIsNotNone(location, "SSOCookieProvider did not issue a login redirect")
        query = parse_qs(urlparse(location).query)
        self.assertIn("federatedOpLoginSession", query, location)
        self.assertIn("keycloak", query.get("federatedOpNames", [""])[0])
        return query["federatedOpLoginSession"][0]

    def _kickoff_federated_op(self, session, login_session_id):
        """Select the Keycloak OP; return the Keycloak authorize URL Knox redirects to."""
        url = f"{self.knoxsso_url}api/v1/websso/federated/op"
        params = {"fedOpSid": login_session_id, "fedOpName": "keycloak"}
        response = session.get(url, params=params, allow_redirects=False,
                               timeout=KNOX_REQUEST_TIMEOUT)
        self.assertIn(response.status_code, (302, 303, 307), response.text)
        location = response.headers.get("Location")
        self.assertIsNotNone(location)
        self.assertTrue(location.startswith(self.keycloak_url), location)
        return location

    def _keycloak_login(self, session, keycloak_authorize_url):
        """Submit alice's credentials to Keycloak; return the Knox callback URL it redirects to."""
        page = session.get(keycloak_authorize_url, allow_redirects=True,
                           timeout=KNOX_REQUEST_TIMEOUT)
        self.assertEqual(page.status_code, 200, "Keycloak did not render a login page")
        parser = _LoginFormParser()
        parser.feed(page.text)
        self.assertIsNotNone(parser.action, "No login form found on the Keycloak page")
        form = dict(parser.inputs)
        form["username"] = KC_USERNAME
        form["password"] = KC_PASSWORD
        action = urljoin(page.url, parser.action)
        submitted = session.post(action, data=form, allow_redirects=False,
                                 timeout=KNOX_REQUEST_TIMEOUT)
        self.assertIn(submitted.status_code, (302, 303), submitted.text)
        location = submitted.headers.get("Location")
        self.assertIsNotNone(location, "Keycloak did not redirect back after login")
        self.assertTrue(location.startswith(self.base_url), location)
        return location

    def _knox_callback(self, session, callback_url):
        """Follow the OP callback into Knox; return the Knox authorization code."""
        response = session.get(callback_url, allow_redirects=False,
                               timeout=KNOX_REQUEST_TIMEOUT)
        self.assertIn(response.status_code, (302, 303), response.text)
        location = response.headers.get("Location")
        self.assertIsNotNone(location)
        self.assertTrue(location.startswith(CLIENT_REDIRECT_URI), location)
        query = parse_qs(urlparse(location).query)
        self.assertIn("code", query, location)
        self.assertEqual(query.get("state", [""])[0], CLIENT_STATE)
        return query["code"][0]

    def _exchange_code(self, session, client_id, client_secret, code):
        """Exchange a Knox authorization code for the Knox token set."""
        url = f"{self.knoxidf_token_url}knoxidf/api/v1/token"
        payload = {
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": CLIENT_REDIRECT_URI,
            "client_id": client_id,
            "client_secret": client_secret,
        }
        response = session.post(url, data=payload, timeout=KNOX_REQUEST_TIMEOUT)
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def _run_full_flow(self):
        """Drive the whole broker flow on a fresh session; return the Knox token set."""
        session = self._new_session()
        client_id, client_secret = self._register_client(session)
        login_session_id = self._start_authorize(session, client_id)
        keycloak_authorize_url = self._kickoff_federated_op(session, login_session_id)
        callback_url = self._keycloak_login(session, keycloak_authorize_url)
        code = self._knox_callback(session, callback_url)
        return self._exchange_code(session, client_id, client_secret, code)

    def test_federation_returns_all_tokens(self):
        """The brokered flow yields access, id, and refresh tokens with a usable token type."""
        tokens = self._run_full_flow()
        self.assertIn("access_token", tokens)
        self.assertIn("id_token", tokens)
        self.assertIn("refresh_token", tokens)
        self.assertEqual(tokens.get("token_type", "").lower(), "bearer")
        self.assertGreater(int(tokens.get("expires_in", 0)), 0)

    def test_id_token_carries_federated_claims(self):
        """The Knox id_token records the OP provenance and a standard email claim."""
        tokens = self._run_full_flow()
        id_token = tokens["id_token"]

        self.assertEqual(get_token_claim(id_token, "federated_idp"), "KEYCLOAK")
        self.assertEqual(
            get_token_claim(id_token, "federated_iss"),
            f"{self.keycloak_url}/realms/knox",
        )
        self.assertTrue(get_token_claim(id_token, "federated_sub"))
        self.assertEqual(get_token_claim(id_token, "email"), KC_EMAIL)

        # The Knox subject is a deterministic UUIDv5 derived from the OP issuer+subject.
        subject = get_token_claim(id_token, "sub")
        self.assertEqual(uuid.UUID(subject).version, 5)

    def test_same_keycloak_user_maps_to_stable_knox_subject(self):
        """Two independent logins by the same OP user resolve to one persisted Knox subject."""
        first = self._run_full_flow()
        second = self._run_full_flow()
        first_sub = get_token_claim(first["id_token"], "sub")
        second_sub = get_token_claim(second["id_token"], "sub")
        self.assertTrue(first_sub)
        self.assertEqual(first_sub, second_sub)


if __name__ == "__main__":
    unittest.main()
