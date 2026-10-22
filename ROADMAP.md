# Roadmap

This project is a maintained fork of [sndyuk/logback-more-appenders](https://github.com/sndyuk/logback-more-appenders).
Upstream is no longer actively maintained, so this fork is published under its own Maven coordinates. The upstream Apache 2.0 license and attribution stay in place.

Each phase is one release. Phases are in the order they should ship, not the order the ideas came up.

## Guiding decisions

- **Maven coordinates:** `io.github.montss:logback-more-appenders`.
  Maven Central does not accept a bare `montss` groupId. GitHub-verified namespaces on the Central Portal take the
  form `io.github.<github-user>`.
- **Java package stays `ch.qos.logback.more.appenders`.** Every existing `logback.xml` / `logback-spring.xml`
  refers to appender classes by fully qualified name. Keeping the package lets people switch by changing only the
  dependency's groupId.
- **Two release lines.**
  - `1.x`: Java 8, Logback 1.2, SLF4J 1.7. Targets Spring Boot 2.x.
  - `2.x`: Java 11+ (17 for Spring Boot 3), Logback 1.4/1.5, SLF4J 2. Targets Spring Boot 3.x / 4.x.
    The groundwork is on the `logback-1.4` branch.
- **Semantic versioning.** Deprecations are minor bumps. Removals and runtime baseline changes are major bumps.
- **Tests before refactors.** The coverage gate (Phase 3) comes before the large dependency jumps and removals so they
  have a safety net.

## Phase 0: Housekeeping (no release)

- [x] Add a CI workflow (GitHub Actions) that runs `mvn verify` on every PR and push to the default branch (#1).
- [x] Protect the default branch with a repository ruleset that targets `~DEFAULT_BRANCH`, not a branch name, so it
      keeps working after a rename. It requires a passing `build` check, applies to admins too, and blocks force
      pushes and deletion.
- [x] Delete branches automatically after their PR is merged.
- [x] **Rename the default branch `master` → `main`**, before Phase 1, so the README, badges and release docs are
      written against `main` from the start.
  - [x] Rename it in GitHub. GitHub redirects old `master` links, and the ruleset follows automatically.
  - [x] Change the push trigger in `.github/workflows/build.yml` to `main`. Workflow triggers use branch names, so
        they do not follow the default branch.
  - [x] Update local clones: `git branch -m master main`, `git fetch origin`, `git branch -u origin/main main`,
        `git remote set-head origin -a`. The `upstream` remote keeps `master`.

## Phase 1: Release 0 → `1.8.9` (upstream parity, new coordinates)

The code is identical to upstream `master`. Only the coordinates and the publishing setup change, so users can switch
with no other differences.

- [ ] groupId → `io.github.montss`. Keep artifactId `logback-more-appenders` and version `1.8.9`.
- [ ] Add the POM metadata Central requires: `name`, `description`, `url`, `licenses`, `developers`, `scm`.
- [ ] Build `-sources` and `-javadoc` jars, sign the artifacts, publish with `central-publishing-maven-plugin`.
      OSSRH was retired in 2025; the Central Portal is the only route now.
- [ ] **Release automation:** pushing a `v*` tag runs a workflow that does three things:
      1. Builds, signs and deploys to Maven Central.
      2. Creates a GitHub Release with generated notes (`gh release create --generate-notes`, grouped by PR labels
         through `.github/release.yml`).
      3. Attaches the jars to the release.
- [ ] Release-prep helper: `mvn versions:set` plus tag, or the release workflow can compute the version from the tag.
- [ ] README: fork notice, new coordinates, migration note ("change only the groupId").
- [ ] Do **not** publish the `JAVA9MODULE_SLF4J17` variant. It is dropped in Phase 2.

## Phase 2: Release 0+1 → `1.9.0` (current branch `dependency-update-fluentd-deprecated`)

Already done on the branch:

- [x] fluency 2.5.1 → 2.6.5. This is the newest version that works with the Jackson version used here; see Phase 4.
- [x] Logback → 1.2.13
- [x] AWS SDK v1 → 1.11.1034, AWS SDK v2 → 2.16.104
- [x] All Maven plugins updated to their latest versions. Added `maven-enforcer-plugin` and `versions-maven-plugin`.
- [x] `fluent-logger` (fluent-logger-java) appenders deprecated. That library is unmaintained
      ([fluent/fluent-logger-java#99](https://github.com/fluent/fluent-logger-java/issues/99)).

Still to do for this release:

- [ ] **Spring Boot compatibility matrix** in the README: rows are library versions; columns are Spring Boot, Logback,
      SLF4J and minimum Java versions. Back it with a CI job that starts a minimal Spring Boot app per supported Boot
      line using the example config, so the matrix is verified and not just written down.
- [ ] **Example configurations** under `examples/`:
  - [ ] `spring-boot-2.7/logback-spring.xml`
  - [ ] `spring-boot-3.x/logback-spring.xml`
  - [ ] Both sanitized: no internal hostnames, service names, company packages, tokens or environment-specific
        values. Use `${FLUENTD_HOST:-localhost}`-style placeholders and comment every option.
- [ ] **Drop the Java 9 module (Jigsaw) variant:** remove `src/java9/`, `pom-JAVA9MODULE_SLF4J17.xml` and the README
      section about it.
- [ ] Release notes call out the `fluent-logger` deprecation and the planned removals in 2.0.0.

## Phase 3: `1.10.0`, code style and test coverage gate

Code style comes first in this phase, so the new tests and every later change are written in the agreed style.

- [ ] Adopt the [Google Java Style](https://google.github.io/styleguide/javaguide.html).
- [ ] Reformat the whole codebase in **one separate commit** with no functional changes. List that commit in
      `.git-blame-ignore-revs` so `git blame` skips it.
- [ ] Enforce the style with `maven-checkstyle-plugin` using the bundled `google_checks.xml`. Bind it to `validate`
      and fail the build on any violation. `google_checks.xml` reports everything as a warning, so set
      `violationSeverity` to `warning`, or the build will never fail.
- [ ] Optional: add Spotless with `google-java-format` so `mvn spotless:apply` fixes formatting automatically and
      Checkstyle only has to check it.
- [ ] Reformat the `logback-1.4` branch the same way before it is merged in Phase 7. Otherwise the merge conflicts on
      every line.
- [ ] JaCoCo with `check` bound to `verify`, so the build fails if coverage drops below the threshold. Start the
      threshold at the measured baseline and raise it step by step toward ≥ 90% line / ≥ 85% branch.
- [ ] Unit tests for every appender, encoder and option, using mocked senders or clients. Integration tests that need
      Fluentd, CloudWatch or Kinesis go in a separate profile, using Testcontainers or LocalStack.
- [ ] Coverage report published as a CI artifact, with a badge in the README.

## Phase 4: `1.11.0`, AWS SDK v1 to its last version and deprecated

AWS SDK for Java v1 reached end of support on 2025-12-31 and gets no more fixes, including security fixes.

- [ ] AWS SDK v1 → latest `1.12.x`.
- [ ] Mark all SDK v1 appenders `@Deprecated`, log a Logback status warning when one is used, and document the
      matching SDK v2 appender for each.

## Phase 5: `1.12.0`, fluency and Jackson to latest

- [ ] Upgrade fluency (2.6.5 → 2.7.x latest) together with the Jackson version it needs. Check the
      Jackson range against Spring Boot 2.7's managed version and update the compatibility matrix.
- [ ] Verify there are no Jackson conflicts with the example Spring Boot apps from Phase 2.

## Phase 6: `1.13.0`, message-template capture for Fluency

This feature was proven in production: alongside the formatted message, send the raw SLF4J template
(`"User {} logged in"`) as its own field whenever it differs from the formatted message. Log backends can then group
and count by template.

- [ ] Build it as an option on the existing `FluencyLogbackAppender` (preferred over a separate subclass), configured
      in XML:
      ```xml
      <captureMessageTemplate>true</captureMessageTemplate>
      <messageTemplateFieldName>message_template</messageTemplateFieldName>
      ```
- [ ] Off by default; works the same in `logback.xml` and `logback-spring.xml`.
- [ ] Full unit test coverage (the Phase 3 gate applies), README docs, and inclusion in the examples.

## Phase 7: `2.0.0`, breaking: modern baseline

- [ ] Merge the `logback-1.4` branch work: Java 11+, Logback 1.4 (and verify 1.5), SLF4J 2.
- [ ] **Remove the AWS SDK v1 appenders.**
- [ ] Remove the deprecated `fluent-logger` appenders (suggested; deprecated since 1.9.0).
- [ ] Compatibility matrix: `2.x` → Spring Boot 3.x / 4.x; `1.x` → Spring Boot 2.x.
- [ ] Decide how long the `1.x` line gets maintenance (security and dependency bumps only), and document it.

## Later / ideas

- Convert these phases into GitHub milestones and issues once the roadmap settles.
- Dependabot or Renovate for dependencies and GitHub Actions.
