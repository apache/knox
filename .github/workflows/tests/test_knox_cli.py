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

########################################################
# These tests are validating the KnoxCLI commands
########################################################

class TestKnoxCLI(unittest.TestCase):
    def setUp(self):
        self.knox = Knox("compose-knox-1")
        self.knox_cli_path = "/knox-runtime/bin/knoxcli.sh"

    def test_knox_cli_create_alias(self):
        """
            Validate creation of alias

            Step:
            - Execute:
            knoxcli.sh create-alias test --cluster cluster1 --value test_value --generate

            Result:
            should export the identity cert file
        :return:
        """
        print(f"\nTesting create-alias command")
        cmd = "{} create-alias test_key --cluster cluster1 --value test_value --generate".format(self.knox_cli_path)
        cmd_output = self.knox.run_knox_cmd(cmd)
        self.assertIn("test_key has been successfully created.", cmd_output)
        print(f"Verified create-alias command output contains new alias")

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
        print(f"Verified create-list-aliases command output contains aliases")

    def test_knox_cli_list_alias(self):
        """
            Validating listing of aliases

            Step:
            - Execute:
            knoxcli.sh list-alias --cluster cluster1

            Result:
            all Alias in the cluster should be listed
        :return:
        """
        print(f"\nTesting list-alias command")
        cmd = "{} list-alias --cluster cluster1".format(self.knox_cli_path)
        cmd_output = self.knox.run_knox_cmd(cmd)
        self.assertIn("Listing aliases for: cluster1", cmd_output)
        print(f"Verified list-alias command output contains alias")

    def test_knox_cli_list_alias_multiple_cluster(self):
        """
            Validating listing of aliases for multiple clusters

            Step:
            - Execute:
            knoxcli.sh list-alias --cluster cluster1,__gateway

            Result:
            all Alias in the clusters should be listed
        :return:
        """
        cmd = "{} list-alias --cluster cluster1,__gateway".format(self.knox_cli_path)
        cmd_output = self.knox.run_knox_cmd(cmd)
        self.assertIn("Listing aliases for: cluster1", cmd_output)
        self.assertIn("Listing aliases for: __gateway", cmd_output)
        print(f"Verified list-alias command output for multiple clusters contains alias")

    def test_knox_cli_list_default_alias(self):
        """
            Validating listing of aliases for default cluster

            Step:
            - Execute:
            knoxcli.sh list-alias

            Result:
            all Alias in the cluster should be listed
        :return:
        """
        cmd = "{} list-alias".format(self.knox_cli_path)
        cmd_output = self.knox.run_knox_cmd(cmd)
        self.assertIn("Listing aliases for: __gateway", cmd_output)
        print(f"Verified list-alias command output for default cluster contains alias")

