# GitHub Workflows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pull request CI, aggregate coverage gating, and version-tag Maven publishing for the Rule Kit repository.

**Architecture:** The root Maven build will produce an aggregate JaCoCo report during `verify`, and two GitHub Actions workflows will consume that build path. Pull requests will run the quality gate and surface coverage; tagged releases will rerun the same gate and only deploy packages after it passes.

**Tech Stack:** GitHub Actions, Maven, JaCoCo, versions-maven-plugin, Java 17/21

---

### Task 1: Add Aggregate Coverage Build Support

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add JaCoCo plugin configuration to the root build**

Add inherited `prepare-agent` execution plus root aggregate reporting during `verify`.

- [ ] **Step 2: Run the focused Maven verification path**

Run: `mvn -q -DskipTests verify`
Expected: build configuration resolves without plugin definition errors

### Task 2: Add Pull Request CI Workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the PR workflow**

Add a workflow that runs on `pull_request`, tests on Java `17` and `21`, generates aggregate coverage, uploads coverage artifacts, and fails below `95%`.

- [ ] **Step 2: Validate workflow syntax locally by reading the file back**

Run: `sed -n '1,240p' .github/workflows/ci.yml`
Expected: workflow contains PR trigger, matrix JDKs, verify command, and coverage gate step

### Task 3: Add Tagged Release Publishing Workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Create the release workflow**

Add a workflow that runs on tags matching `v*`, derives the Maven version from the tag, reruns verify plus coverage gate, and deploys to GitHub Packages only after success.

- [ ] **Step 2: Validate workflow syntax locally by reading the file back**

Run: `sed -n '1,260p' .github/workflows/release.yml`
Expected: workflow contains tag trigger, dynamic version step, verify step, coverage gate step, and deploy step

### Task 4: Restore Green Tests And Raise Coverage

**Files:**
- Modify: `rule-kit-java/rule-kit-sdk/src/test/java/com/cars24/rulekit/sdk/RuleKitClientTest.java`
- Modify: `rule-kit-java/rule-kit-sdk/src/test/java/com/cars24/rulekit/sdk/RuleKitClientNativeTest.java`
- Modify: additional test files only if aggregate coverage remains below threshold

- [ ] **Step 1: Update or add tests to match the current condition-tree model without reverting user changes**

Write meaningful tests around the branch’s current supported rule shape.

- [ ] **Step 2: Run the targeted failing tests**

Run: `mvn -q -pl rule-kit-java/rule-kit-sdk -am test`
Expected: SDK tests pass

- [ ] **Step 3: Add more targeted tests if aggregate coverage is still below `95%`**

Prefer tests for currently uncovered production behavior over synthetic assertions.

### Task 5: Verify End To End

**Files:**
- Review: `pom.xml`
- Review: `.github/workflows/ci.yml`
- Review: `.github/workflows/release.yml`

- [ ] **Step 1: Run the full reactor verification**

Run: `mvn -q verify`
Expected: all tests pass and aggregate coverage report is generated

- [ ] **Step 2: Inspect aggregate coverage outcome**

Run: `find . -path '*jacoco-aggregate/jacoco.xml' -o -path '*site/jacoco-aggregate/jacoco.xml'`
Expected: aggregate coverage report path is present

- [ ] **Step 3: Summarize remaining repository settings**

Document that GitHub branch protection must require the PR workflow status check if merge blocking is desired in the repository settings.
