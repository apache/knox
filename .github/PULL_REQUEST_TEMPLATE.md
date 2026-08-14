(It is very **important** that you created an Apache Knox JIRA for this change and that the PR title/commit message includes the Apache Knox JIRA ID!)

[KNOX-1234](url) - A short description of the change

## What changes were proposed in this pull request?

(Please fill in changes proposed in this fix)

## How was this patch tested?

(Please explain how this patch was tested. For instance: running automated unit/integration tests, manual tests. Please write down your test steps as detailed as possible)

## Integration Tests
(Please add or update integration tests [`.github/workflows/tests`](.github/workflows/tests) for the feature you are adding. If no unit test is added, please explain why. Check out [`.github/workflows/tests/README.md`](./workflows/tests/README.md) for instructions)

### Opt-in test suites (PR labels)
Some integration suites are expensive and are **not** run on every PR. Add the corresponding label to this PR to run them:

| Label | Runs |
|-------|------|
| `test-federation` | KnoxIDF federation E2E (`test_knoxidf_federation.py`). Stands up a real Keycloak as an external OpenID Provider and drives the full broker flow. Adds a few minutes (image pull + realm import). |
| `skip-tests` | Skips the entire Docker Compose test job. |

**When to add the label:** these labels only take effect on runs triggered by opening the PR, pushing a commit, or reopening the PR — the workflow does not run on a label change. Add the label **before opening the PR** (or before your next push). Adding it after the checks have already finished will **not** start a new run; push a commit or close/reopen the PR to trigger one with the label applied.

## UI changes
(If this patch involves UI changes, please attach a screen-shot; otherwise, remove this)

Please review [Knox Contributing Process](https://cwiki.apache.org/confluence/display/KNOX/Contribution+Process#ContributionProcess-GithubWorkflow) before opening a pull request.
