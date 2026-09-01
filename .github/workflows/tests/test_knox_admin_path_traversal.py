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

"""Integration tests for Admin API path-traversal hardening.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_get, knox_put

PROVIDER_CONFIG_BODY = (
    "<gateway>"
    "<provider><role>authentication</role><name>Anonymous</name>"
    "<enabled>true</enabled></provider>"
    "</gateway>"
)


class TestKnoxAdminPathTraversal(unittest.TestCase):
    """Admin API must reject traversal names and still serve legitimate ones."""

    def setUp(self):
        self.base_url = gateway_base_url()
        self.admin_api_url = self.base_url + "gateway/knoxldap/api/v1"
        self.providerconfig_url = self.admin_api_url + "/providerconfig"
        self.admin_auth = HTTPBasicAuth("admin", "admin-password")

    def test_admin_api_reachable(self):
        """Positive control: admin can list provider configs (200)."""
        response = knox_get(self.providerconfig_url, auth=self.admin_auth)
        self.assertEqual(
            response.status_code,
            200,
            msg=f"admin should reach the admin API; got "
            f"{response.status_code}: {response.text}",
        )

    def test_providerconfig_upload_requires_auth(self):
        """The admin API must reject anonymous callers (401)."""
        response = knox_put(
            self.providerconfig_url + "/testprovider",
            data=PROVIDER_CONFIG_BODY,
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(response.status_code, 401)

    def test_providerconfig_traversal_name_is_rejected(self):
        """Traversal provider-config names must be refused with 400, not written.

        Both the single- and double-encoded forms of ``../gateway-site.xml`` must
        fail: ``..%2f...`` carries an encoded path separator, and ``..%252f...``
        is decoded once at the servlet layer to ``..%2f...``. Neither may reach a
        file write.
        """
        for name in ("..%2fgateway-site.xml", "..%252fgateway-site.xml"):
            with self.subTest(name=name):
                response = knox_put(
                    self.providerconfig_url + "/" + name,
                    data=PROVIDER_CONFIG_BODY,
                    headers={"Content-Type": "application/json"},
                    auth=self.admin_auth,
                )
                self.assertEqual(
                    response.status_code,
                    400,
                    msg=f"Expected 400 for traversal name {name!r}, got "
                    f"{response.status_code}: {response.text}",
                )


if __name__ == "__main__":
    unittest.main()
