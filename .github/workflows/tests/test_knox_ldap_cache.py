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

"""Integration tests for Shiro authentication caching in KnoxLdapRealm.

Runs against the live knox + ldap docker-compose stack via the knoxldapcache
topology, whose ShiroProvider wires the Ehcache-backed Knox cache manager and
enables authentication caching:
    main.cacheManager = org.apache.knox.gateway.shirorealm.KnoxCacheManager
    main.securityManager.cacheManager = $cacheManager
    main.ldapRealm.authenticationCachingEnabled = true

Guarantees:
  * caching   - repeated authentications of the same principal all succeed
                (200); the first bind populates the EhcacheShiro cache and the
                subsequent ones are served through it, so caching must not
                break the auth result. (Cache hit/miss is confirmed separately
                by inspecting the knox logs for KnoxCacheManager messages.)
  * security  - a wrong password is still rejected (401), so caching keyed on
                the principal never authenticates bad credentials; and a
                DN-injection username is still rejected (401), so the RFC 4514
                escaping remains effective with caching enabled.
"""

import unittest

from common_utils import basic_auth_get, gateway_base_url

# Repeat count for the cache round-trip: first request populates the cache,
# the rest exercise the cached path.
REPEAT = 5


class TestKnoxLdapCache(unittest.TestCase):
    """Auth caching works and does not weaken credential or DN-injection checks."""

    def setUp(self):
        self.base_url = gateway_base_url()
        # Topology name derives from the filename knoxldapcache.xml.
        self.topology_url = self.base_url + "gateway/knoxldapcache/auth/api/v1/pre"

    def test_repeated_auth_is_served_and_succeeds(self):
        """Repeated logins of the same user all succeed with caching enabled."""
        for attempt in range(REPEAT):
            with self.subTest(attempt=attempt):
                response = basic_auth_get(
                    self.topology_url, "guest", "guest-password"
                )
                self.assertEqual(
                    response.status_code,
                    200,
                    f"guest attempt {attempt} should authenticate; "
                    f"got {response.status_code}",
                )
                self.assertEqual(
                    response.headers.get("x-knox-actor-username"), "guest"
                )

    def test_wrong_password_still_rejected(self):
        """Caching is keyed on the principal but must not accept a bad password."""
        # Warm the cache with a valid login first.
        warm = basic_auth_get(self.topology_url, "guest", "guest-password")
        self.assertEqual(warm.status_code, 200)
        # Same principal, wrong password: must be rejected despite a cache entry.
        response = basic_auth_get(self.topology_url, "guest", "wrong-password")
        self.assertEqual(
            response.status_code,
            401,
            f"wrong password must be rejected; got {response.status_code}",
        )
        self.assertNotIn("x-knox-actor-username", response.headers)

    def test_dn_injection_still_rejected_with_caching(self):
        """DN-escaping stays effective with caching on: injection user is 401."""
        response = basic_auth_get(
            self.topology_url, "guest,ou=people,dc=proxy,dc=org", "guest-password"
        )
        self.assertEqual(
            response.status_code,
            401,
            f"injection username must be rejected; got {response.status_code}",
        )
        self.assertNotIn("x-knox-actor-username", response.headers)


if __name__ == "__main__":
    unittest.main()
