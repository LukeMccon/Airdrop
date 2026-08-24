# AIRDR-10 GitHub Actions Hardening Design

## Goal

Update every external GitHub Action used by the repository to its latest stable, compatible release, pin every invocation to the reviewed release commit, automate controlled update proposals, and restrict release write permission to the job that publishes the GitHub release.

## Current State

The three workflow files contain 17 external `uses:` invocations across eight distinct actions. They currently reference floating major tags or, for TruffleHog, the mutable `main` branch. The release workflow grants `contents: write` at workflow scope, so its build and Modrinth jobs receive write access they do not require. The repository has no GitHub Actions Dependabot configuration and no automated policy test for action references.

## Considered Approaches

### Pin the existing action versions

This is the smallest change and removes mutable references, but it preserves avoidable action-version debt. Several actions have newer stable majors intended for current GitHub-hosted runners.

### Update, pin, enforce, and automate

This is the selected approach. Each distinct action is checked against its official repository, updated to the latest stable release compatible with GitHub-hosted `ubuntu-latest`, and pinned to that release's full 40-character commit SHA. A repository test prevents later floating references, while Dependabot proposes reviewed SHA updates.

### Add a third-party workflow security scanner

A specialized scanner could enforce broader workflow policies, but it would add another external action and supply-chain trust boundary. AIRDR-10 can be enforced with a focused repository-local test and GitHub's existing dependency automation.

## Action Selection and Pinning

For each distinct action, implementation will inspect the official GitHub repository's releases and tags. The chosen version must be a stable release, support the action's existing inputs, and support GitHub-hosted `ubuntu-latest` runners. Preview, release-candidate, branch, and moving major references are excluded. If the newest stable major has an incompatible runner or input requirement, the newest compatible stable release remains selected and the reason is recorded in the implementation plan.

Every external invocation will use this form:

```yaml
uses: owner/action@0123456789abcdef0123456789abcdef01234567 # v1.2.3
```

The SHA is the commit resolved by the selected release tag. The inline comment is the exact readable release version. Repeated invocations of the same action use the same SHA and comment.

## Automated Policy Test

A focused JUnit test will scan every YAML file directly under `.github/workflows`. For each `uses:` line, it will ignore repository-local references beginning with `./` and require every other reference to contain:

- an `owner/repository` or remote reusable-workflow path;
- an `@` reference containing exactly 40 lowercase or uppercase hexadecimal characters; and
- a non-empty inline version comment beginning with `v` followed by a digit.

The test will report the workflow path, line number, and invalid line so failures are actionable. It will also assert that at least one external invocation was inspected, preventing an empty or incorrect scan from passing silently. Because it runs through the existing Gradle test suite, the policy is checked by normal local and CI validation without adding another action.

## Dependency Automation

`.github/dependabot.yml` will enable the `github-actions` package ecosystem at repository root on a weekly schedule. Dependabot will therefore propose action SHA changes through ordinary pull requests, where the updated commit and readable version can be reviewed before merging. The configuration will limit concurrent action update pull requests to avoid update noise.

## Permission Model

The release workflow will set workflow-level `contents: read`. The `publish-github` job alone will override this with job-level `contents: write`, because it uploads files to the GitHub release. The `build` and `publish-modrinth` jobs will inherit read-only contents permission. CI and develop pull-request workflows will retain or add explicit read-only contents permission.

No workflow receives pull-request, issue, package, deployment, or identity-token write permission as part of this change.

## Validation

Implementation will use test-first development for the policy test: introduce representative failing expectations against the current floating references, then pin the workflows until the test passes. Validation will include:

- the focused workflow-policy JUnit test;
- a scan of all workflow `uses:` entries and their resolved SHA/comment pairs;
- assertions that workflow-level release permission is read-only and only `publish-github` has `contents: write`;
- `./gradlew test` and `./gradlew clean build`;
- review of each selected version and SHA against its official action repository; and
- an independent agent review before merge into `4.0-beta`.

## Non-Goals

- Redesign workflow triggers, job topology, artifact flow, or release destinations.
- Add organization-wide GitHub policy enforcement.
- Replace GitHub-hosted runners or pin runner images.
- Automatically merge Dependabot pull requests.
- Add a general-purpose workflow linter or security scanner.
