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
import os
import unittest
import requests
import urllib3

# Suppress InsecureRequestWarning
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class TestKnoxHealth(unittest.TestCase):
    def test_admin_api_health(self):
        """
        Basic health check to ensure Knox is up and running.
        We expect a response 200 to indicate the server is up.
        """
        url = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")
        print(f"Checking connectivity to {url}...")
        try:
            response = requests.get(url + "health/v1/ping", verify=False, timeout=30)
            print(f"Received status code: {response.status_code}")
            self.assertEqual(response.status_code, 200)
        except requests.exceptions.ConnectionError:
            self.fail("Failed to connect to Knox on port 8443 - Connection refused")
        except Exception as e:
            self.fail(f"Health check failed with unexpected error: {e}")

if __name__ == '__main__':
    unittest.main()

