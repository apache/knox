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

"""KnoxIDF tests with tokens carrying the roles of the embedded Knox LDAP server.

knoxidf-token enriches tokens through the LDAP user params provider, which reads the user from the
embedded Knox LDAP server; in the REST roles lookup stack that server's roles-lookup interceptor
replaces the user's memberOf with its roles. Every TestKnoxIDF test runs again; the authorization
code flows request the roles scope and check the access tokens' roles claim. The provider takes the
roles from the memberOf role DNs, so the claim is only right if those DNs name roles under the
embedded LDAP server's groups base.
"""

import unittest

from test_knoxidf import TestKnoxIDF


class TestKnoxIDFWithRestRoleLookup(TestKnoxIDF):
    """guest is in no group; its only role is assigned to its user id in roles.json."""

    EXPECTED_ROLES = ["platform:viewer"]


class TestKnoxIDFGroupRolesWithRestRoleLookup(TestKnoxIDF):
    """sam's roles are those of its groups."""

    EXPECTED_ROLES = ["platform:analyst", "platform:scientist"]

    def setUp(self):
        super().setUp()
        self.username = "sam"
        self.password = "sam-password"
del TestKnoxIDF


if __name__ == "__main__":
    unittest.main()
