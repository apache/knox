# Adding Test Cases to GitHub Workflow

This directory contains Python integration tests that run as part of the GitHub workflow.

## Directory Structure

- `test_*.py`: Python test files containing test cases.
- `requirements.txt`: Python dependencies required for running the tests.

## How to Add a New Test Case

1. **Create a New Test File**:
   Create a new Python file in this directory (`.github/workflows/tests/`). The filename **must** start with `test_` (e.g., `test_auth.py`) to be automatically discovered by the test runner.

2. **Implement Test Logic**:
   Use `pytest` to structure your tests. Test functions must start with `test_`; test classes must start with `Test` and must not define an `__init__` method. Existing `unittest.TestCase` tests are also supported by pytest.

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
   
   import os

   import requests
   import urllib3

   # Suppress InsecureRequestWarning since we use verify=False for self-signed certs
   urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

   # Default to localhost for local debugging outside Docker.
   BASE_URL = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")


   def test_my_endpoint():
       """Verify that the WebHDFS endpoint returns a successful response."""
       url = f"{BASE_URL}gateway/sandbox/webhdfs/v1/?op=LISTSTATUS"

       # verify=False is needed for the dev environment's self-signed certificate.
       response = requests.get(url, verify=False, timeout=30)

       assert response.status_code == 200


   def test_another_endpoint():
       """Add another independently discovered test."""
       response = requests.get(
           f"{BASE_URL}gateway/health/v1/ping",
           verify=False,
           timeout=30,
       )

       assert response.status_code == 200
   ```

3. **Add Dependencies**:
   If your test requires additional Python libraries (other than `requests`), add them to `requirements.txt` in this directory.

## Organizing Tests in Subdirectories

You can organize tests into subdirectories (e.g., `tests/auth/`, `tests/proxy/`). Pytest recursively discovers matching test files:

1. Test files must match the `test_*.py` pattern.
2. An `__init__.py` file is optional unless the tests need the directory to be importable as a package.

**Example structure:**

```text
tests/
├── test_health.py
├── auth/
│   └── test_auth.py
└── proxy/
    └── test_proxy.py
```

## How It Works

The tests run in a dedicated Docker container defined in `../compose/docker-compose.yml`.

1. The `tests` service mounts this directory (`.github/workflows/tests/`) to `/tests` inside the container.
2. It installs dependencies from `requirements.txt`.
3. It waits briefly for the `knox` service to start.
4. It runs `pytest`, excluding the single-EKU suites that are executed separately by the workflow.

## Skipping Tests on Pull Requests

If you want to skip the integration tests for a specific Pull Request (e.g., documentation changes), add the label **`skip-tests`** to the PR.

## Running Tests Locally

You can run these tests locally using Docker Compose from the Knox source directory:

```bash
docker compose -f ./.github/workflows/compose/docker-compose.yml up --exit-code-from tests tests
```

This will build knox image and start the Knox environment and run the tests. The `tests` container will exit once tests are complete.
To shut down the environment 

```bash
docker compose -f ./.github/workflows/compose/docker-compose.yml down
```
