# AIRDR-22: Proportional Engineering Guidance

## Goal

Make the expected engineering scale explicit in the project-level agent guidance: Airdrop is a Minecraft plugin, so changes should be reliable and maintainable without importing enterprise-scale architecture or process.

## Change

Add the same `Project Scale` section near the top of `AGENTS.md` and `CLAUDE.md`, after each file's introduction and before issue-tracking instructions.

The section will state bluntly that this is a Minecraft plugin, not a multimillion-dollar enterprise project. It will direct agents to choose the simplest change that solves the actual problem, follow existing patterns, and test behavior that matters. It will discourage speculative abstractions, unnecessary compatibility layers, excessive defensive code, and unjustified process overhead.

The guidance will preserve a focused quality bar for failures that can duplicate or lose items, affect the economy, corrupt configuration, or destabilize the server.

## Scope

- Change only `AGENTS.md` and `CLAUDE.md` during implementation.
- Keep all existing project, build, architecture, and issue-tracking guidance intact.
- Use matching wording in both files so different coding agents receive the same direction.

## Verification

- Confirm both files contain the approved section in the intended location.
- Confirm the wording is identical in both files.
- Review the diff to ensure no unrelated guidance changed.
- No build or automated tests are needed for documentation-only changes.
