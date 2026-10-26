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
- **Tests before refactors.** The coverage gate (added in Phase 2, raised in Phase 3) comes before the large dependency jumps and removals so they
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

Verified: upstream's published `com.sndyuk:logback-more-appenders:1.8.9` has the same dependencies as upstream
`master`, and our jar has exactly the same classes and files.

- [x] groupId → `io.github.montss`. Keep artifactId `logback-more-appenders` and version `1.8.9`.
- [x] POM metadata Central requires: `url`, `scm` and `issueManagement` point to the fork. `developers` keeps sndyuk
      and adds montss.
- [x] `release` profile: sources and javadoc jars, signing with `maven-gpg-plugin`, and publishing with
      `central-publishing-maven-plugin`. It replaces upstream's OSSRH setup, which no longer works since OSSRH was
      shut down in 2025.
- [x] **Release automation** (`.github/workflows/release.yml`): pushing a `v*` tag runs a workflow that:
      1. Fails if the tag does not match the project version.
      2. Builds, signs and publishes to Maven Central.
      3. Creates the GitHub release with generated notes, putting `.github/release-notes/<tag>.md` first when that
         file exists, and attaches the jars and their signatures.
- [x] Release secrets live in a `release` environment that only `v*` tags can use. `v*` tags cannot be moved or
      deleted, except deliberately by an admin.
- [x] README: fork notice, new coordinates, migration note ("change only the groupId").
- [x] **Make GitHub detect the license:** full Apache License 2.0 text in `LICENSE`. The short per-file notice moved
      to `src/license/header.txt`, which `license-maven-plugin` now reads.
- [x] **Copyright notice:** a `NOTICE` file keeps the upstream line unchanged, as Apache 2.0 §4 requires, and adds
      the fork's line: `Copyright (c) 2012 sndyuk <sanada@sndyuk.com>` and
      `Copyright (c) 2026 montss <montss.dev@gmail.com>`. The per-file headers keep sndyuk's notices as they are;
      only their license URL changed to `https`. `mvn license:format` was not run, because it would overwrite the
      differing upstream years.
- [x] Do **not** publish the `JAVA9MODULE_SLF4J17` variant. Upstream stopped publishing it with 1.8.9 too.
- [x] Tag `v1.8.9` on `main` and check the release on Maven Central and GitHub. Released 2026-10-24.

**Release steps (from now on):** set the version in a PR (`mvn versions:set -DnewVersion=X.Y.Z`), optionally add
`.github/release-notes/vX.Y.Z.md`, merge it, then tag that commit on `main` with `vX.Y.Z` and push the tag.

**Release notes format:** follow the [Spring Boot release notes](https://github.com/spring-projects/spring-boot/releases/tag/v4.0.9)
layout. Use only the sections that have entries, in this order: `## :warning: Deprecations`, `## :star: New Features`,
`## :lady_beetle: Bug Fixes`, `## :notebook_with_decorative_cover: Documentation`, `## :hammer: Dependency Upgrades`
(always its own section; one line each, "Upgrade X from A to B", "Replace X A with Y B" or "Add X B", covering
runtime dependencies and build plugins), and `## :heart: Contributors`. When the notes file
exists it is the whole release text; without it, GitHub generates the notes.

Release follow-ups:

- [ ] **One-click releases:** after merging to `main`, start the whole release cycle with one action in GitHub
      (e.g. a "Run workflow" button) instead of the manual version PR and tag push. Details to be agreed before work
      starts.

## Phase 2: Release 0+1 → `1.9.0` (current branch `dependency-update-fluentd-deprecated`)

Already done on the branch:

- [x] fluency 2.5.1 → 2.6.5. This is the newest version that works with the Jackson version used here; see Phase 4.
- [x] Logback → 1.2.13
- [x] AWS SDK v1 → 1.11.1034, AWS SDK v2 → 2.16.104
- [x] All Maven plugins updated to their latest versions. Added `maven-enforcer-plugin` and `versions-maven-plugin`.
- [x] `fluent-logger` (fluent-logger-java) appenders deprecated. That library is unmaintained
      ([fluent/fluent-logger-java#99](https://github.com/fluent/fluent-logger-java/issues/99)).

- [x] Synced with `main` (the 1.8.9 release setup) and version set to `1.9.0`.
- [x] Maven plugins updated again: license 5.1.2, compiler 3.16.0, dependency 3.11.0, versions 2.23.0, enforcer 3.6.3.

Still to do for this release:

- [x] **Spring Boot compatibility matrix** in the README: the 1.x line runs on Spring Boot 2.7
      (Java 8 and 17) and on Spring Boot 3.0 and 3.1 (Java 17); 2.x is planned for Spring Boot 3.x and later on Java 17
      and later. Checked by hand with Spring Boot 2.7.18, 3.0.13 and 3.1.12 on Corretto 8u512 and 17.0.21, sending to
      Fluentd 1.19.4 with the example configs.
  - [x] Checked in CI: the `spring-boot-compat` job in `build.yml` runs once per matrix row. It builds the library
        from the commit, starts Fluentd in Docker, runs a minimal Spring Boot app (`compat/spring-boot-app`) with the
        matching example config, and fails unless the events reach the console and Fluentd with no Logback
        problems. `compat/check.sh` runs the same check locally.
- [x] **Example configurations** `examples/spring-boot-2.7/logback-spring.xml` and
      `examples/spring-boot-3.x/logback-spring.xml`, based on production configs. Sanitized: no internal hostnames,
      company classes, environment checks or extra fields. The Fluentd host comes from `${FLUENTD_HOST:-localhost}`,
      and every option is commented. Neither example sets `<name>` in the appender: on Logback 1.4+ it
      silently leaves the root logger without appenders.
- [x] **Tests that check something:** `LogbackAppenderTest` had 8 tests and no assertions. It now sends through
      `FluencyLogbackAppender` to an in-process Fluentd (`FakeFluentdServer`) and checks every record, and it fails
      on any Logback warning or error. `SampleConfigurationsTest` loads the sample configs the README links to and
      fails on any warning or error. That found a non-existent `<label>` option in the Fluency sample, now removed.
- [x] **JaCoCo:** report on every build (summary on the CI job page, full report as an artifact). The build fails
      below 49% lines / 37% branches, just under the measured 50.0% / 38.2%. The old 59.8% counted code that tests
      ran without checking anything.
- [x] Release notes (`.github/release-notes/v1.9.0.md`) in the Spring Boot release notes format, with a separate
      Dependency Upgrades section. They call out the `fluent-logger` deprecation and its removal in 2.0.0.

## Phase 3: `1.10.0`, code style, CloudWatch and Kinesis tests, AWS SDK v1 deprecated

Each item below is its own PR, merged in this order. Code style comes first so the new tests are written in the
agreed style. The tests come before the AWS SDK v1 upgrade so they can show the upgrade changes nothing.

1. **Code style**
   - [x] Adopt the [Google Java Style](https://google.github.io/styleguide/javaguide.html).
   - [x] Reformat the whole codebase in **one separate commit** with no functional changes, using
         google-java-format 1.7 (the last release that runs on Java 8). That commit is listed in
         `.git-blame-ignore-revs` so `git blame` skips it. For a local clone, run
         `git config blame.ignoreRevsFile .git-blame-ignore-revs` once.
   - [x] Enforce the style with `maven-checkstyle-plugin` 3.6.0 and Checkstyle 9.3 (the last release that runs on
         Java 8; 10.x needs Java 11), using the bundled `google_checks.xml` over main and test code. It runs in
         `validate`, with `violationSeverity` set to `warning` so any violation fails the build.
   - [x] Not adding Spotless: its versions that still run on Java 8 are old. Format with google-java-format 1.7 by
         hand when needed; Checkstyle catches anything that slips through.
   - [x] License headers: `license-maven-plugin` 4.6 (the last that runs on Java 8; 5.x needs Java 11) checks them
         in `validate`. All files use one `/*` header layout; each keeps its own copyright line.
   - [ ] Reformat the `logback-1.4` branch the same way before it is merged in Phase 6. Otherwise the merge conflicts
         on every line.
2. **CloudWatch and Kinesis tests**
   - [x] Tests for `CloudWatchLogbackAppender`, `KinesisStreamLogbackAppender` and their SDK v2 counterparts. They use
         in-memory fakes of the SDK clients, so they need no Docker or AWS account and run in the normal
         `mvn verify`. Like the real services, the fakes reject writes to missing destinations. The tests check what
         is sent: log group and stream creation, sequence tokens, batching limits, message truncation, stream
         rolling, partition keys, Kinesis stream creation, and failed puts.
   - [x] Raised the JaCoCo minimums to 77% lines / 63% branches, just under the measured 78.4% / 64.2% (was 50.0% /
         38.2%). Keep raising them step by step toward ≥ 90% lines / ≥ 85% branches in later releases.
   - [x] ~~Coverage badge in the README.~~ Dropped: it needs an external service or a bot committing to `main`.
3. **AWS SDK v1 to its last version, deprecated**
   - [x] Fixed first, one PR each, with tests: a missing fixed CloudWatch log stream was never created (#9), Kinesis
         retried partially failed batches wrongly (#10), and the SDK v1 Kinesis appender ignored profile and default
         credentials (#11). Two older upstream bugs are tracked as issues: #13 and #14.
   - [x] AWS SDK v1 1.11.1034 → `1.12.797`, the latest `1.12.x`. The 61 tests passed before and after the upgrade,
         with the same results.
   - [x] Marked the SDK v1 appenders (`CloudWatchLogbackAppender`, `KinesisStreamLogbackAppender` and their bases)
         `@Deprecated`. Each logs a Logback status warning when it starts, naming its SDK v2 appender.
   - [x] README links to the
         [AWS SDK for Java v1 end-of-support announcement](https://aws.amazon.com/blogs/developer/announcing-end-of-support-for-aws-sdk-for-java-v1-x-on-december-31-2025/):
         support ended on 2025-12-31, so since January 2026 v1 gets no more fixes, including security fixes.
   - [x] The release notes link to it too (item 4).
   - [x] AWS SDK v2 stays at 2.16.104 for this release. See "Later / ideas".
4. **Release**
   - [x] Release notes `.github/release-notes/v1.10.0.md` and version `1.10.0`.
   - [ ] Tag `v1.10.0` on `main` and check the release on Maven Central and GitHub.

## Phase 4: `1.11.0`, fluency and Jackson to latest

- [ ] Upgrade fluency (2.6.5 → 2.7.x latest) together with the Jackson version it needs. Check the
      Jackson range against Spring Boot 2.7's managed version and update the compatibility matrix.
- [ ] Verify there are no Jackson conflicts with the example Spring Boot apps from Phase 2.

## Phase 5: `1.12.0`, message-template capture for Fluency

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

## Phase 6: `2.0.0`, breaking: modern baseline

- [ ] Merge the `logback-1.4` branch work: Java 11+, Logback 1.4 (and verify 1.5), SLF4J 2.
- [ ] **Remove the AWS SDK v1 appenders.**
- [ ] Remove the deprecated `fluent-logger` appenders (suggested; deprecated since 1.9.0).
- [ ] Compatibility matrix: `2.x` → Spring Boot 3.x / 4.x; `1.x` → Spring Boot 2.x.
- [ ] Decide how long the `1.x` line gets maintenance (security and dependency bumps only), and document it.

## Later / ideas

- Drop the Java 9 module (Jigsaw) variant: remove `src/java9/`, `pom-JAVA9MODULE_SLF4J17.xml` and the README section
  about it. Moved out of Phase 2 to a later release.
- Convert these phases into GitHub milestones and issues once the roadmap settles.
- Dependabot or Renovate for dependencies and GitHub Actions.
- AWS SDK v2 2.16.104 → a newer 2.x. It is hundreds of releases behind; decide the target version (and check it
  still supports Java 8) before starting.
