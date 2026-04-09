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
import unittest
from requests.auth import HTTPBasicAuth

from common_utils import assert_hsts_header, gateway_base_url, knox_get


########################################################
# This test is verifying the global HSTS headers for 404 response.
# It executes new GET request on non-existent Knox path
# It verifies header is present with the correct value.
########################################################

class TestKnoxConfigs(unittest.TestCase):
    def setUp(self):
        self.base_url = gateway_base_url()
        self.non_existent_path = self.base_url + "gateway/not-exists"

    def test_auth_service_guest(self):
        """
        Verifies header is present with the correct value
        """
        print(f"\nTesting global HSTS config for 404 response")
        response = knox_get(
            self.non_existent_path,
            auth=HTTPBasicAuth('admin', 'admin-password'),
        )

        print(f"Status Code: {response.status_code}")
        self.assertEqual(response.status_code, 404)

        assert_hsts_header(self, response)
        print(f"Verified Strict-Transport-Security: {response.headers['Strict-Transport-Security']}")

