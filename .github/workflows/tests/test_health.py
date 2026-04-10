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
import json
import unittest

import requests

from common_utils import assert_hsts_header, gateway_base_url, knox_get


class TestKnoxHealth(unittest.TestCase):
    """Integration checks for the gateway health REST API (ping and metrics)."""

    # Top-level keys expected in /metrics JSON (aligned with Java GatewayHealthFuncTest).
    _METRICS_TOP_LEVEL_KEYS = frozenset(
        {"timers", "histograms", "counters", "gauges", "version", "meters"}
    )

    def setUp(self):
        self.base_url = gateway_base_url()

    def test_health_ping_ok_and_hsts(self):
        """Ping returns 200, body OK, and the gateway sends the expected HSTS header."""
        url = self.base_url + "gateway/health/v1/ping"
        print(f"Checking connectivity to {url}...")
        try:
            response = knox_get(url)
            print(f"Received status code: {response.status_code}")
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.text.strip(), "OK")

            assert_hsts_header(self, response)
        except requests.exceptions.ConnectionError:
            self.fail("Failed to connect to Knox on port 8443 - Connection refused")
        except Exception as e:
            self.fail(f"Health check failed with unexpected error: {e}")

    def test_health_metrics_returns_json(self):
        """Metrics with pretty=true returns 200 and a JSON object with application/json content type."""
        url = self.base_url + "gateway/health/v1/metrics?pretty=true"
        response = knox_get(url)
        self.assertEqual(response.status_code, 200)

        content_type = response.headers.get("Content-Type", "")
        self.assertIn("application/json", content_type)

        payload = json.loads(response.text)
        self.assertIsInstance(payload, dict)

    def test_health_metrics_contains_core_fields(self):
        """Metrics JSON should expose the same top-level keys as the Java GatewayHealthFuncTest."""
        url = self.base_url + "gateway/health/v1/metrics?pretty=true"
        response = knox_get(url)
        self.assertEqual(response.status_code, 200)
        payload = json.loads(response.text)
        self.assertTrue(
            self._METRICS_TOP_LEVEL_KEYS.issubset(payload.keys()),
            msg=f"Missing keys: {self._METRICS_TOP_LEVEL_KEYS - set(payload.keys())}",
        )

    def test_health_metrics_without_pretty_returns_json(self):
        """Metrics without pretty still returns 200, parseable JSON, and the same top-level keys as pretty."""
        url = self.base_url + "gateway/health/v1/metrics"
        response = knox_get(url)
        self.assertEqual(response.status_code, 200)
        self.assertIn("application/json", response.headers.get("Content-Type", ""))
        payload = json.loads(response.text)
        self.assertIsInstance(payload, dict)
        self.assertTrue(
            self._METRICS_TOP_LEVEL_KEYS.issubset(payload.keys()),
            msg=f"Missing keys: {self._METRICS_TOP_LEVEL_KEYS - set(payload.keys())}",
        )

    def test_health_ping_content_type_is_plain_text(self):
        """Ping response declares text/plain Content-Type and body OK."""
        url = self.base_url + "gateway/health/v1/ping"
        response = knox_get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.text.strip(), "OK")
        content_type = response.headers.get("Content-Type", "")
        self.assertIn("text/plain", content_type)

if __name__ == '__main__':
    unittest.main()

