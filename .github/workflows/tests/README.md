# Adding Test Cases to GitHub Workflow

This directory contains Python integration tests that run as part of the GitHub workflow.

## Directory Structure

- `test_*.py`: Python test files containing test cases.
- `requirements.txt`: Python dependencies required for running the tests.

## How to Add a New Test Case

1. **Create a New Test File**:
   Create a new Python file in this directory (`.github/workflows/tests/`). The filename **must** start with `test_` (e.g., `test_auth.py`) to be automatically discovered by the test runner.

2. **Implement Test Logic**:
   Use the `unittest` framework to structure your tests.

   ```python
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
   import requests
   import os
   import urllib3

   # Suppress InsecureRequestWarning since we use verify=False for self-signed certs
   urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

   class TestMyFeature(unittest.TestCase):
       def setUp(self):
           # Get the Knox Gateway URL from environment variables
           # Default to localhost for local debugging outside Docker
           self.base_url = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")

       def test_my_endpoint(self):
           """
           Description of what this test checks.
           """
           url = f"{self.base_url}gateway/sandbox/webhdfs/v1/?op=LISTSTATUS"
           
           print(f"Testing URL: {url}")
           
           # Make the request
           # verify=False is needed for the dev environment's self-signed certs
           response = requests.get(url, verify=False)
           
           # Assertions
           self.assertEqual(response.status_code, 200)
           # Add more assertions as needed
   ```

3. **Add Dependencies**:
   If your test requires additional Python libraries (other than `requests`), add them to `requirements.txt` in this directory.

## How It Works

The tests run in a dedicated Docker container defined in `../compose/docker-compose.yml`.

1. The `tests` service mounts this directory (`.github/workflows/tests/`) to `/tests` inside the container.
2. It installs dependencies from `requirements.txt`.
3. It waits for the `knox` service to be ready.
4. It runs `python -m unittest discover -p 'test_*.py'` to find and execute all test files.

## Running Tests Locally

You can run these tests locally using Docker Compose from the `.github/workflows/compose` directory:

```bash
cd .github/workflows/compose
docker-compose up --build --abort-on-container-exit
```

This will start the Knox environment and run the tests. The `tests` container will exit once tests are complete.

