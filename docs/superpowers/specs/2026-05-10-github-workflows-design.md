# GitHub Workflows Design

## Goal

Add GitHub Actions automation that:

- runs tests for pull requests,
- shows aggregate project coverage,
- fails pull requests when total coverage is below `95%`,
- blocks tagged releases when tests or coverage fail, and
- publishes versioned Maven packages to GitHub Packages from version tags.

## Scope

This design covers:

- root Maven coverage configuration,
- GitHub Actions workflows for PR validation and tagged releases,
- dynamic release versioning from tags such as `v1.2.3`,
- coverage reporting in workflow summaries and artifacts,
- targeted test additions needed to make the repository pass the new gate.

This design does not cover:

- GitHub repository branch protection settings,
- non-GitHub package registries,
- benchmark publishing.

## Constraints

- The repository is a multi-module Maven build.
- Client-facing published artifacts are `rule-kit-sdk` and `rule-kit-spring-boot-starter`.
- The worktree already contains unrelated in-progress rule-engine changes and those must not be reverted.
- The current branch already has failing tests, so the new CI must be introduced together with the fixes or tests needed for a green baseline.

## Decisions

### Workflow split

Use two workflows:

1. `ci.yml` for pull requests.
2. `release.yml` for version tags matching `v*`.

This keeps PR validation and publish concerns separate while reusing the same Maven verification path.

### Coverage model

Use JaCoCo aggregate coverage for the full reactor instead of per-module thresholds. The workflow will parse the aggregate XML report and fail below `95%` line coverage.

### Release versioning

The release workflow will derive the Maven version from the pushed tag by stripping the leading `v`, then run a non-committed `versions:set` before deploy. This allows tags like `v1.2.3` to publish package version `1.2.3` without changing the checked-in `pom.xml` version.

### Release blocking

The release workflow will run the same verification and coverage gate before any deploy step. If verification fails, no package publication happens.

## Implementation Notes

- Add JaCoCo plugin configuration in the root `pom.xml`.
- Generate aggregate reports during `verify`.
- Upload coverage artifacts from CI for inspection.
- Write a small shell step in Actions to extract total line coverage from `jacoco.xml` and emit both a summary and a hard failure when below `95`.
- Keep deploy behavior scoped by existing module-level `maven.deploy.skip` settings.

## Risks

- Aggregate coverage across all modules may expose real test gaps in modules that were not previously gated.
- GitHub Actions status checks only block merge when branch protection is configured to require them.

## Acceptance Criteria

- A pull request triggers tests on Java `17` and `21`.
- The workflow displays aggregate coverage and fails below `95%`.
- A version tag like `v1.2.3` triggers release validation and publishes packages as version `1.2.3` only if validation passes.
- The repository test suite passes with the new workflow expectations.
