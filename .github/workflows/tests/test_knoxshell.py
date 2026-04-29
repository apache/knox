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
import subprocess

class TestKnoxShell(unittest.TestCase):
    def setUp(self):
        self.base_url = os.environ.get("KNOX_GATEWAY_URL", "https://knox:8443/")
        if not self.base_url.endswith("/"):
            self.base_url += "/"
        self.knoxshell_dir = "/knoxshell"
        self.knoxshell_bin = os.path.join(self.knoxshell_dir, "bin", "knoxshell.sh")
        
        # Check if knoxshell exists
        if not os.path.exists(self.knoxshell_bin):
            self.fail(f"KnoxShell binary not found at {self.knoxshell_bin}")
            
        # Ensure it's executable
        os.chmod(self.knoxshell_bin, 0o755)

    def test_1_build_trust_store(self):
        """
        Test basic connection by building the trust store.
        """
        print(f"\nTesting buildTrustStore against {self.base_url}")

        # Run buildTrustStore
        result = subprocess.run(
            [self.knoxshell_bin, "buildTrustStore", self.base_url],
            cwd=self.knoxshell_dir,
            capture_output=True,
            text=True
        )
        
        print(f"STDOUT: {result.stdout}")
        print(f"STDERR: {result.stderr}")
        
        self.assertEqual(result.returncode, 0, f"buildTrustStore failed with return code {result.returncode}")

    def test_2_init_and_list_token(self):
        """
        Test acquiring a token via init and verifying it via list.
        """
        topology_url = self.base_url + "gateway/knoxldap"
        print(f"\nTesting init and list against {topology_url}")

        # Run init and pass credentials via stdin
        # The prompt expects username then password
        credentials = "guest\nguest-password\n"
        
        init_result = subprocess.run(
            [self.knoxshell_bin, "init", topology_url],
            cwd=self.knoxshell_dir,
            input=credentials,
            capture_output=True,
            text=True
        )
        
        print(f"INIT STDOUT: {init_result.stdout}")
        print(f"INIT STDERR: {init_result.stderr}")
        
        self.assertEqual(init_result.returncode, 0, f"init failed with return code {init_result.returncode}")
        
        # Run list to verify the token
        list_result = subprocess.run(
            [self.knoxshell_bin, "list"],
            cwd=self.knoxshell_dir,
            capture_output=True,
            text=True
        )
        
        print(f"LIST STDOUT: {list_result.stdout}")
        print(f"LIST STDERR: {list_result.stderr}")
        
        self.assertEqual(list_result.returncode, 0, f"list failed with return code {list_result.returncode}")
        self.assertIn("knoxldap", list_result.stdout, "Token for knoxldap not found in list output")

if __name__ == '__main__':
    unittest.main()
