# Releasing

Pushing a tag matching `v*` runs `.github/workflows/release.yml`: unit tests and lint, then a signed release APK, then a GitHub Release with auto-generated notes. Failing tests or lint block the release.

## Cut a release

1. Make sure `main` is green and contains what you want to ship.
2. Tag and push:

   ```sh
   git tag v1.2.3
   git push origin v1.2.3
   ```

3. Watch the `Release` run in GitHub Actions. On success the release holds `simmer-v1.2.3.apk`.

The tag must look like `vMAJOR.MINOR.PATCH` with an optional `-suffix`. `scripts/release-version.sh` derives:

- `versionName`: the tag without the leading `v` (`v0.1.0-rc1` gives `0.1.0-rc1`).
- `versionCode`: `major*10000 + minor*100 + patch`, ignoring the suffix (`v1.2.3` gives `10203`, `v0.1.0-rc1` gives `100`). Minor and patch must be 99 or less.
- pre-release: true when the tag contains a hyphen.

Android refuses to install an APK whose `versionCode` is lower than the installed one. An rc and its final release (`v0.1.0-rc1`, `v0.1.0`) share a `versionCode`, so if the installer refuses the final over an rc, uninstall the rc first.

## Pre-release (rc) flow

1. Tag `v0.1.0-rc1` and push. The release is marked pre-release.
2. Install the APK on a phone and check it (`docs/manual-testing.md`).
3. Problems: fix, then tag `v0.1.0-rc2`.
4. Good: tag `v0.1.0` on the same or a later commit.

To redo a failed run, delete the tag locally and on the remote (`git push origin :refs/tags/v0.1.0-rc1`), delete any half-created GitHub Release, and tag again.

## Secrets

The workflow reads four repository secrets: `KEYSTORE_BASE64` (the keystore file, base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. It decodes the keystore into `$RUNNER_TEMP` and passes `KEYSTORE_PATH` plus the other three as env vars to Gradle.

Create the keystore and set all four secrets with the wizard (needs `gh` logged in):

```sh
scripts/release-keystore-wizard.sh
```

## Keystore backup

The keystore signs every release. If it is lost, phones with the app installed cannot update to a build signed with a new key.

- Keep the keystore file and its passwords in a password manager or other offline backup, separate from the repo.
- Never commit the keystore or passwords.
- GitHub Secrets cannot be read back, so they are not a backup.
