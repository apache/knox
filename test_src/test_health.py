import unittest
import requests
import urllib3

# Suppress InsecureRequestWarning
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class TestKnoxHealth(unittest.TestCase):
    def test_admin_api_health(self):
        """
        Basic health check to ensure Knox is listening on port 8443.
        We expect a response (even 401/403/404) to indicate the server is up.
        """
        url = "https://localhost:8443/"
        print(f"Checking connectivity to {url}...")
        try:
            response = requests.get(url, verify=False, timeout=30)
            print(f"Received status code: {response.status_code}")
            # If we get a response, the server is reachable.
            # 404 or 401 is acceptable for a root path ping if no topology is specified,
            # proving the server is listening.
            self.assertIsNotNone(response.status_code)
        except requests.exceptions.ConnectionError:
            self.fail("Failed to connect to Knox on port 8443 - Connection refused")
        except Exception as e:
            self.fail(f"Health check failed with unexpected error: {e}")

if __name__ == '__main__':
    unittest.main()

