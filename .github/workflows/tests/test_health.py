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

from common_utils import (
    METRICS_TOP_LEVEL_KEYS,
    assert_hsts_header,
    gateway_base_url,
    health_metrics_pretty_dict,
    knox_get,
    knox_post,
)


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


class TestHealthGatewayExtended(unittest.TestCase):
    """Anonymous HEALTH topology: gateway-status, ping variants, metrics keys, routing."""

    def setUp(self):
        self.base_url = gateway_base_url()

    def test_health_gateway_status_returns_ok_or_pending_plain_text(self):
        """gateway-status is 200 text/plain with body OK or PENDING."""
        url = self.base_url + "gateway/health/v1/gateway-status"
        r = knox_get(url)
        self.assertEqual(r.status_code, 200)
        self.assertIn("text/plain", r.headers.get("Content-Type", ""))
        self.assertIn(r.text.strip(), ("OK", "PENDING"))

    def test_health_ping_post_returns_ok(self):
        """POST /v1/ping matches GET semantics for the health service."""
        url = self.base_url + "gateway/health/v1/ping"
        r = knox_post(url)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.text.strip(), "OK")

    def test_health_ping_sets_cache_control_no_store(self):
        """Ping uses must-revalidate,no-cache,no-store (see PingResource)."""
        url = self.base_url + "gateway/health/v1/ping"
        r = knox_get(url)
        self.assertEqual(r.status_code, 200)
        cc = r.headers.get("Cache-Control", "")
        self.assertIn("no-store", cc)
        self.assertIn("no-cache", cc)

    def test_health_metrics_pretty_includes_all_core_top_level_keys(self):
        """Pretty metrics JSON includes timers/histograms/counters/gauges/version/meters."""
        payload = health_metrics_pretty_dict(self.base_url)
        self.assertTrue(
            METRICS_TOP_LEVEL_KEYS.issubset(payload.keys()),
            msg=f"Missing keys: {METRICS_TOP_LEVEL_KEYS - set(payload.keys())}",
        )

    def test_health_metrics_without_pretty_includes_same_top_level_keys(self):
        """Metrics without ?pretty= still expose the same registry sections."""
        url = self.base_url + "gateway/health/v1/metrics"
        r = knox_get(url)
        self.assertEqual(r.status_code, 200)
        payload = json.loads(r.text)
        self.assertTrue(METRICS_TOP_LEVEL_KEYS.issubset(payload.keys()))

    def test_health_metrics_version_value_is_non_empty_string(self):
        """The version entry in metrics JSON is a string."""
        payload = health_metrics_pretty_dict(self.base_url)
        ver = payload.get("version")
        self.assertIsInstance(ver, str)
        self.assertTrue(len(ver) > 0)

    def test_unknown_topology_returns_404(self):
        """Requests to an undeployed topology name fail with 404."""
        url = self.base_url + "gateway/not-a-deployed-topology/v1/ping"
        r = knox_get(url)
        self.assertEqual(r.status_code, 404)

    def test_health_gateway_status_includes_hsts(self):
        """gateway-status uses the same global Strict-Transport-Security as other gateway responses."""
        url = self.base_url + "gateway/health/v1/gateway-status"
        r = knox_get(url)
        self.assertEqual(r.status_code, 200)
        assert_hsts_header(self, r)

    def test_health_metrics_includes_hsts(self):
        """Metrics JSON responses include the expected HSTS header."""
        url = self.base_url + "gateway/health/v1/metrics?pretty=true"
        r = knox_get(url)
        self.assertEqual(r.status_code, 200)
        assert_hsts_header(self, r)

    def test_health_gateway_status_cache_control_no_store(self):
        """gateway-status sets Cache-Control with no-cache/no-store like ping."""
        url = self.base_url + "gateway/health/v1/gateway-status"
        r = knox_get(url)
        self.assertEqual(r.status_code, 200)
        cc = r.headers.get("Cache-Control", "")
        self.assertIn("no-store", cc)
        self.assertIn("no-cache", cc)


class TestHealthMetricsSectionShapes(unittest.TestCase):
    """Dropwizard metric registry JSON: each major section is an object."""

    @classmethod
    def setUpClass(cls):
        cls._payload = health_metrics_pretty_dict(gateway_base_url())

    def test_metrics_timers_section_is_dict(self):
        self.assertIsInstance(self._payload["timers"], dict)

    def test_metrics_histograms_section_is_dict(self):
        self.assertIsInstance(self._payload["histograms"], dict)

    def test_metrics_counters_section_is_dict(self):
        self.assertIsInstance(self._payload["counters"], dict)

    def test_metrics_gauges_section_is_dict(self):
        self.assertIsInstance(self._payload["gauges"], dict)

    def test_metrics_meters_section_is_dict(self):
        self.assertIsInstance(self._payload["meters"], dict)


if __name__ == "__main__":
    unittest.main()
