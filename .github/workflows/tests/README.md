# Adding Test Cases to GitHub Workflow

This directory contains Python integration tests that run as part of the GitHub workflow.

**Quick reference:** Tests hit a **real** Knox Gateway (and LDAP) in Docker. The CI workflow is **Apache Knox Docker Compose Tests** (`.github/workflows/tests.yml`). The test runner in Compose is **`pytest`** with JUnit output (`test-results.xml`). Set **`KNOX_GATEWAY_URL`** for where the client should connect (Compose sets `https://knox:8443/`). Prefer **`common_utils`** (`gateway_base_url`, `knox_get`, `knox_post`) for TLS/timeouts consistent with the rest of this suite.

## Directory Structure

- `test_*.py`: Python test files containing test cases.
- `requirements.txt`: Python dependencies required for running the tests.
- `common_utils.py`: Shared helpers (gateway URL, HTTP calls with dev TLS, HSTS checks, etc.); recommended for new tests.
- `Dockerfile`: Builds the `tests` image by copying `requirements.txt`, `common_utils.py`, and `test_*.py` into `/tests`.

## How to Add a New Test Case

1. **Create a New Test File**:
   Create a new Python file in this directory (`.github/workflows/tests/`). The filename **must** start with `test_` (e.g., `test_auth.py`) to be automatically discovered by the test runner.

2. **Implement Test Logic**:
   Use the `unittest` framework to structure your tests. You can include multiple test methods in a single class, and multiple classes in a single file. Each method starting with `test_` will be executed as a separate test case.

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

       def test_another_endpoint(self):
           """
           Another test case in the same class.
           """
           # ... implementation ...
           pass
   ```

   **Tip:** For consistency with the rest of this directory, consider using `from common_utils import gateway_base_url, knox_get` instead of raw `requests` calls (see `test_health.py`).

3. **Add Dependencies**:
   If your test requires additional Python libraries (other than `requests`), add them to `requirements.txt` in this directory.

## Organizing Tests in Subdirectories

You can organize tests into subdirectories (e.g., `tests/auth/`, `tests/proxy/`). For the test runner to discover them:

1. The subdirectory **must** contain an `__init__.py` file (it can be empty).
2. The test files inside must still match the `test_*.py` pattern.

**Example structure:**

```text
tests/
├── test_health.py
├── auth/
│   ├── __init__.py
│   └── test_auth.py
└── proxy/
    ├── __init__.py
    └── test_proxy.py
```

**Note:** The current `Dockerfile` in this folder copies only top-level `test_*.py` into the image. Nested packages are not picked up unless you extend the Docker build (e.g., adjust `COPY` or layout). CI and the default Compose file expect flat `test_*.py` files here unless the build is updated.

## How It Works

The tests run in a dedicated Docker container defined in `../compose/docker-compose.yml`.

1. The `tests` service mounts this directory (`.github/workflows/tests/`) to `/tests` inside the container.
2. It installs dependencies from `requirements.txt`.
3. It waits for the `knox` service to be ready.
4. It runs `python -m unittest discover -p 'test_*.py'` to find and execute all test files.

**Additional detail (default Compose as checked in):** In the usual workflow, test sources are **`COPY`'d** into the `tests` image at **build** time (see `Dockerfile` in this directory), not bind-mounted. To run against your working tree without rebuilding, you can merge **`../compose/docker-compose.tests-mount.yml`**, which bind-mounts `../tests` read-only (optional; some Docker Desktop setups may have bind-mount issues). The `tests` service command runs **`pytest --junitxml=test-results.xml`** (see `docker-compose.yml`). After you change `test_*.py` locally, **`docker compose ... build tests`** (or **`build tests --no-cache`**) ensures the image matches the repo; CI builds `knox-dev` and `tests` so PRs stay in sync.

## Skipping Tests on Pull Requests

If you want to skip the integration tests for a specific Pull Request (e.g., documentation changes), add the label **`skip-tests`** to the PR.

## Running Tests Locally

You can run these tests locally using Docker Compose from the Knox source directory:

```bash
docker compose -f ./.github/workflows/compose/docker-compose.yml build knox-dev tests
docker compose -f ./.github/workflows/compose/docker-compose.yml up -d
docker compose -f ./.github/workflows/compose/docker-compose.yml up --exit-code-from tests tests
```

This will build knox image and start the Knox environment and run the tests. The `tests` container will exit once tests are complete.

A shorter variant (Compose may build what is missing):

```bash
docker compose -f ./.github/workflows/compose/docker-compose.yml up --exit-code-from tests tests
```

To shut down the environment:

```bash
docker compose -f ./github/workflows/compose/docker-compose.yml down
```

Optional: add `--volumes` to `down` if you want to remove named volumes as well.

## Linting (pylint)

Before pushing changes to Python files in this directory, install the repo git hook once from the Knox source root:

```bash
./githooks/install.sh
```

The pre-push hook runs pylint only on **modified** `.py` files under `.github/workflows/tests/` (in the commits being pushed). The local venv at `.github/workflows/tests/.venv/` installs test dependencies from `requirements.txt` plus pylint (local lint only; not installed in the Docker test image).

To lint your current working tree changes manually:

```bash
./.github/workflows/tests/run_pylint.sh
```

## Troubleshooting

- **Stale test count or old code in the container:** Rebuild the `tests` image (`docker compose -f ./.github/workflows/compose/docker-compose.yml build tests --no-cache`) or use `docker-compose.tests-mount.yml` as described above.
- **Connection refused to Knox:** Ensure `ldap` and `knox` services are up; the compose file uses a short sleep before `pytest`—on slow hosts you may need to wait longer or adjust the compose command.
