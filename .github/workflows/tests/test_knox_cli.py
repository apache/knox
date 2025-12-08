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
from util.knox import Knox

class TestKnoxCLI(unittest.TestCase):
    def setUp(self):
        self.knox = Knox("compose-knox-1")
        self.knox_cli_path = "/knox-runtime/bin/knoxcli.sh"

    def test_knox_cli_create_list_aliases(self):
        """
            Validating creating and listing of aliases for multiple cluster

            Step:
            - Execute:
            knoxcli.sh create-list-aliases --alias aliasx1 --value value --alias aliasx2 --value value --cluster clusterX
             --alias aliasy1 --value value --alias aliasy2 --value value --cluster clusterY

            Result:
            all Alias in the clusters should be created and listed
        :return:
        """
        print(f"\nTesting create-list-aliases command")
        cmd = ("{} create-list-aliases --alias aliasx1 --value value --alias aliasx2 --value value --cluster clusterX"
               " --alias aliasy1 --value value --alias aliasy2 --value value --cluster clusterY").format(self.knox_cli_path)
        cmd_output = self.knox.run_knox_cmd(cmd)
        self.assertIn("Listing aliases for: clusterX", cmd_output)
        self.assertIn("Listing aliases for: clusterY", cmd_output)
        self.assertIn("2 alias(es) have been successfully created: [aliasx1, aliasx2]", cmd_output)
        self.assertIn("2 alias(es) have been successfully created: [aliasy1, aliasy2]", cmd_output)
        print(f"Verified command output contains aliases")
