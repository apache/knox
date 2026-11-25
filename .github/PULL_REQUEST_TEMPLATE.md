(It is very **important** that you created an Apache Knox JIRA for this change and that the PR title/commit message includes the Apache Knox JIRA ID!)

[KNOX-XYZ1](https://issues.apache.org/jira/browse/KNOX-XYZ1) - A short description of the change

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
| `test-ha` | KnoxIDF HA E2E (`test_knoxidf_ha.py`). Recreates `knox` on a shared PostgreSQL database and adds a second gateway, `knox2`; verifies KnoxIDF registry changes made through one gateway are picked up by the other. Adds a few minutes (PostgreSQL image pull + second gateway startup). |
| `skip-tests` | Skips the entire Docker Compose test job. |

**When to add the label:** these labels only take effect on runs triggered by opening the PR, pushing a commit, or reopening the PR — the workflow does not run on a label change. Add the label **before opening the PR** (or before your next push). Adding it after the checks have already finished will **not** start a new run; push a commit or close/reopen the PR to trigger one with the label applied.

## Performance implications
(Please describe the performance impact of this change, e.g. changes on the request hot path, new remote/DB calls, caching and TTL behaviour, lock contention, memory footprint, or behaviour in HA/multi-instance deployments. If there is no impact, say so explicitly and explain why, e.g. "None, because ..."; do not remove this section)

## UI changes
(If this patch involves UI changes, please attach a screen-shot; otherwise, remove this)

Please review [Knox Contributing Process](https://cwiki.apache.org/confluence/display/KNOX/Contribution+Process#ContributionProcess-GithubWorkflow) before opening a pull request.
