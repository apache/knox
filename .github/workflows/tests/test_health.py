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

