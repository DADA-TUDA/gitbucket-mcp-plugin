# Task Plan

- [x] Inspect the latest GitHub Actions failure and isolate the failing step.
- [x] Patch the GitBucket setup detection in the integration test runner.
- [x] Run local verification for the release path.
- [ ] Push the fix and confirm the remote GitHub workflow turns green.

## Review

- Root cause: the GitHub integration workflow and integration script used the wrong GitBucket default password, so fixture creation hit HTTP 401 on a fresh instance.
- Fix: switch the workflow/docs/script to the real GitBucket default credentials and keep the setup probe as a fallback for install-form environments.
- Verification: `./gradlew test shadowJar` and a full `python3 scripts/integration_test.py` run against a downloaded GitBucket 4.46.0 war both passed locally.
