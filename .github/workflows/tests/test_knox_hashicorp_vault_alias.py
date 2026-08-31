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

"""End-to-end test of Knox's HashiCorp Vault remote alias service.

This is opt-in and NOT part of the default test run (the base docker-compose
ignores it). It runs only under docker-compose.hashicorp-vault.yml, which stands
up a dev-mode HashiCorp Vault and reconfigures Knox to use RemoteAliasService
backed by that Vault. See that override file for how to run it.

What it proves (relevant to Spring / Spring Vault upgrades): the running gateway
can still talk to Vault through spring-vault. The flow is:
  1. write an alias via the Knox admin alias REST API (as user "admin"),
  2. read it back through the admin API listing,
  3. confirm the secret actually landed in Vault by querying Vault's HTTP API
     directly at secret/data/knox/<cluster>/<alias>,
  4. delete the alias via the admin API and confirm it drops out of the listing.
"""

import os
import unittest
import uuid

import requests
from requests.auth import HTTPBasicAuth

from common_utils import (
    KNOX_REQUEST_TIMEOUT,
    gateway_base_url,
    knox_delete,
    knox_get,
    knox_post,
)

# Demo LDAP user that belongs to the "admin" group; the admin topology's ACL
# (admin;*;*) authorizes this user for the alias API.
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin-password"

# Must match the HashiCorp Vault settings in hashicorp-vault/gateway-site.xml.
VAULT_SECRETS_ENGINE = "secret"
VAULT_PATH_PREFIX = "knox"

# Injected by docker-compose.hashicorp-vault.yml for the direct-to-Vault check.
VAULT_ADDR = os.environ.get("VAULT_ADDR", "http://localhost:8200")
VAULT_TOKEN = os.environ.get("VAULT_TOKEN", "myroot")


class TestKnoxHashicorpVaultAlias(unittest.TestCase):
    """Round-trip aliases through the Knox admin API and verify them in Vault."""

    def setUp(self):
        self.admin_auth = HTTPBasicAuth(ADMIN_USER, ADMIN_PASSWORD)
        self.aliases_base = gateway_base_url() + "gateway/admin/api/v1/aliases"
        # Unique, lowercase names per test run (Knox lowercases alias names, and
        # Vault paths are case-sensitive), so reruns never collide.
        suffix = uuid.uuid4().hex[:12]
        self.cluster = "vault-it-" + suffix
        self.alias = "test-alias-" + suffix
        self.secret_value = "s3cret-" + suffix

    def tearDown(self):
        # Best-effort cleanup so a failed assertion mid-test does not leak state.
        try:
            knox_delete(self._alias_url(), auth=self.admin_auth)
        except requests.RequestException:
            pass

    def _alias_url(self):
        return f"{self.aliases_base}/{self.cluster}/{self.alias}"

    def _list_aliases(self):
        response = knox_get(f"{self.aliases_base}/{self.cluster}", auth=self.admin_auth)
        self.assertEqual(response.status_code, 200)
        return response.json().get("aliases", [])

    def _read_from_vault(self):
        # KV v2 read: the secrets engine "secret" exposes reads under
        # secret/data/<logical-path>. Knox stores the value under the "data" key.
        url = (
            f"{VAULT_ADDR}/v1/{VAULT_SECRETS_ENGINE}/data/"
            f"{VAULT_PATH_PREFIX}/{self.cluster}/{self.alias}"
        )
        return requests.get(
            url,
            headers={"X-Vault-Token": VAULT_TOKEN},
            timeout=KNOX_REQUEST_TIMEOUT,
        )

    def test_alias_write_is_stored_in_vault(self):
        """A PUT/POST via the admin API stores the secret in HashiCorp Vault."""
        create = knox_post(
            self._alias_url(),
            auth=self.admin_auth,
            data={"value": self.secret_value},
        )
        self.assertEqual(create.status_code, 201)

        # Readable through Knox's own alias listing (which itself reads Vault).
        self.assertIn(self.alias, self._list_aliases())

        # And present in Vault directly, proving the write reached the backend.
        vault_response = self._read_from_vault()
        self.assertEqual(vault_response.status_code, 200)
        stored = vault_response.json()["data"]["data"]
        self.assertEqual(stored.get("data"), self.secret_value)

    def test_alias_delete_removes_from_listing(self):
        """Deleting an alias via the admin API removes it from the listing."""
        create = knox_post(
            self._alias_url(),
            auth=self.admin_auth,
            data={"value": self.secret_value},
        )
        self.assertEqual(create.status_code, 201)
        self.assertIn(self.alias, self._list_aliases())

        delete = knox_delete(self._alias_url(), auth=self.admin_auth)
        self.assertEqual(delete.status_code, 200)

        self.assertNotIn(self.alias, self._list_aliases())

    def test_admin_alias_api_requires_authentication(self):
        """The alias API is protected: an unauthenticated request is rejected."""
        response = knox_get(f"{self.aliases_base}/{self.cluster}")
        self.assertEqual(response.status_code, 401)


if __name__ == "__main__":
    unittest.main()
