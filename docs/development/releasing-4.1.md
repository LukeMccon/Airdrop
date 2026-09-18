# Cut 4.1 from the verified release branch

AIRDR-83 tracks release preparation; AIRDR-79 tracks the release notes.
The draft PR promotes `release/4.1` into `main` after the owner verifies it.
Its starting point is develop commit
`d6a98626ad3d8d308feff1698aeef1387a33a19f`, including the starter price of `10.0`.
Merging the existing stable history into this branch did not change its files.

PR #92 (the player catalog) is excluded from 4.1. PR #96 (removing dependency
checksum verification) was also unmerged when this candidate was prepared and
is not included. Merge only fixes selected for this release into the release
branch, and repeat affected verification whenever the candidate changes.

## Verify the candidate before making the PR ready

Use the release worktree, Paper **1.21.11**, and Java **21**. Record the full
commit and the runtime JAR checksum with the test results:

```bash
git status --short
git rev-parse HEAD
./gradlew --no-daemon --dependency-verification=strict -PreleaseTag=4.1.0 \
  clean test build verifyApiCompatibility verifyReleaseArtifact lightkeeperTest
sha256sum build/libs/Airdrop-4.1.0.jar
```

The working tree must be clean. Keep `airdropPluginVersion=4.1.0-SNAPSHOT` in
`gradle.properties`: the release checks require that source value, and
`-PreleaseTag=4.1.0` generates stable JAR names and metadata.

Test `build/libs/Airdrop-4.1.0.jar` on a disposable server and a copy of an
existing 4.0 installation, using the intended economy and permissions plugins:

- Fresh startup creates one starter priced at 10. An unprivileged non-operator
  cannot request it; a player granted its permission pays exactly 10.
- Missing economy support and insufficient funds reject paid requests without
  delivery. An explicitly free package still requires permission.
- Existing package contents, prices, and custom settings survive the upgrade.
  Invalid reloads retain the previous live configuration.
- Package editing preserves items through save, cancel, and inventory actions.
  `/airdrop packages` remains an administrator command in this release.
- Incoming and landed messages appear at the right times. Protection-cancelled
  landings do not place barrels and attempt one refund after a confirmed charge.
- Paid, landed barrels retain their contents across a graceful server restart.
  Falling drops during shutdown retain the documented 4.0 limitations; this
  release does not promise a refund for those failures.
- `/airdrop version` reports plugin 4.1.0, extension API 1.0.0, Paper 1.21.11,
  and Java 21. `/airdrop status` reports readiness and the expected provider.

## Merge the approved candidate into main

After the owner verifies the candidate, mark the PR ready, obtain the required
review, and wait for all checks. Main requires an approving review and a
code-owner review. Use a merge commit to preserve the release history.

Set `verified_candidate` to the full SHA recorded during testing, not a newly
looked-up head. Run these commands in the clean release worktree:

```bash
set -euo pipefail
release_pr=$(gh pr view release/4.1 --repo LukeMccon/Airdrop --json number --jq .number)
verified_candidate=REPLACE_WITH_THE_FULL_TESTED_SHA
test "$(gh pr view "$release_pr" --repo LukeMccon/Airdrop --json headRefOid --jq .headRefOid)" = "$verified_candidate"
gh pr ready "$release_pr" --repo LukeMccon/Airdrop
gh pr checks "$release_pr" --repo LukeMccon/Airdrop --watch
gh pr merge "$release_pr" --repo LukeMccon/Airdrop --merge --match-head-commit "$verified_candidate"

release_commit=$(gh pr view "$release_pr" --repo LukeMccon/Airdrop --json mergeCommit --jq .mergeCommit.oid)
git fetch github main
git merge-base --is-ancestor "$release_commit" github/main
git diff --exit-code "$verified_candidate" "$release_commit" -- .
```

Stop on any failed command. If the merged tree differs, verify that new tree
before proceeding. Wait for the CI run whose head SHA equals `release_commit`
to pass, including LightKeeper. Do not tag a moving branch name.

## Publishing the GitHub release starts distribution

The workflow in [release.yml](../../.github/workflows/release.yml) runs on
`release.published`. Merging, pushing a tag, or creating a draft release alone
does not publish artifacts. GitHub's default branch is `develop`, so create
the tag explicitly at the verified main merge commit.

After the checks above pass, switch to that commit and extract only the 4.1
notes. The extraction removes internal comments and makes documentation links
work on both GitHub and Modrinth:

```bash
set -euo pipefail
git switch --detach "$release_commit"
python3 - <<'PY'
from pathlib import Path
import re
text = Path('CHANGELOG.md').read_text(encoding='utf-8')
notes = text.split('# Airdrop 4.0 release notes:', 1)[0]
notes = re.sub(r'<!--.*?-->\s*', '', notes, flags=re.S)
notes = notes.replace('(docs/', '(https://github.com/LukeMccon/Airdrop/blob/v4.1.0/docs/')
Path('/tmp/airdrop-4.1.0-release-notes.md').write_text(notes.strip() + '\n', encoding='utf-8')
PY
git tag -a v4.1.0 "$release_commit" -m 'Airdrop 4.1.0'
git push github refs/tags/v4.1.0
test "$(git ls-remote github 'refs/tags/v4.1.0^{}' | cut -f1)" = "$release_commit"
gh release create v4.1.0 --repo LukeMccon/Airdrop --verify-tag --draft \
  --title 'Airdrop 4.1.0: Delivery messages and a supported plugin API' \
  --notes-file /tmp/airdrop-4.1.0-release-notes.md
```

Review the draft release, then publish it as stable with:

```bash
set -euo pipefail
test "$(git ls-remote github 'refs/tags/v4.1.0^{}' | cut -f1)" = "$release_commit"
gh release edit v4.1.0 --repo LukeMccon/Airdrop --verify-tag --draft=false --prerelease=false --latest
```

This starts the release workflow's build, tests, API/artifact checks, and
LightKeeper run. Once its build passes, it uploads the runtime JAR to GitHub
and the runtime, sources, and Javadocs to Modrinth. The short Modrinth project
description is maintained separately. Publishing the detailed reference is
disabled unless the repository variable `PUBLISH_MODRINTH_DOCS` is explicitly
set to `true`; leave it unset or `false` to preserve the project description.

`MODRINTH_TOKEN` and `MODRINTH_PROJECT_ID` must remain configured in GitHub.
Both were present during preparation; publishing credentials were not exercised.
The workflow's default loader and game version are `paper` and `1.21.11`.

## Verify hosted artifacts before calling the release complete

- Confirm `build`, `publish-github`, and `publish-modrinth` pass (AIRDR-45).
  `publish-modrinth-docs` is skipped unless reference publication was explicitly
  enabled.
- Download the GitHub and Modrinth runtime JARs and compare their SHA-256 hashes
  with the verified release build. Confirm their metadata and install one of
  the downloaded artifacts for a final startup check.
- Confirm the Modrinth sources and Javadoc downloads are available and the
  published Maven coordinates resolve for the integration guide (AIRDR-41).
- Check that documentation links reach the repository guide. Only when
  reference publication was explicitly enabled, run
  `MODRINTH_PROJECT_ID=wslZpHU2 ./scripts/modrinth-docs check-remote`
  from the tag checkout.
- Bring the main release merge back into develop through a normal PR so the
  release notes and history carry forward. Keep PR #92 out of the 4.1 tag.

If publication fails partway, inspect which destinations already contain 4.1.0
and resume only the missing work. Keep the published tag fixed; a runtime code
correction after publication requires a new patch release.
