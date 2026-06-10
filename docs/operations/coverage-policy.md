# CircleGuard – Coverage Policy

This document defines the JaCoCo code coverage thresholds, reporting configuration, and enforcement rules for CircleGuard's CI pipelines.

---

## Thresholds by Environment

| Pipeline         | Minimum Line Coverage | Blocks Build? |
|------------------|-----------------------|---------------|
| DEV (`Jenkinsfile.dev`)   | 0% (report-only)   | No            |
| STAGE (`Jenkinsfile.stage`) | 40%              | Yes — `UNSTABLE` if below |
| MASTER (`Jenkinsfile.master`) | 0% (report-only) | No          |

The stage pipeline is the enforcement gate. A build with coverage below 40% becomes `UNSTABLE` and does not advance to Docker Build or Deploy.

---

## What Counts as Covered

JaCoCo measures **line coverage** (the primary metric used for thresholds). A line is covered if at least one test executed it.

Excluded from coverage:
- Test classes (`**/*Test*`)
- Spring Boot main application entry points (`**/*Application*`)
- Generated sources (Lombok-generated code is not excluded by default, but Lombok annotations reduce visible branches)

Included in coverage:
- Service layer classes
- Controller layer classes
- Repository and mapper classes
- Domain model classes with logic

---

## Coverage Report Location

| Type    | Path |
|---------|------|
| Aggregate XML | `build/reports/jacoco-aggregate/jacocoTestReport.xml` |
| Aggregate HTML | `build/reports/jacoco-aggregate/html/index.html` |
| Per-service HTML | `services/<svc>/build/reports/jacoco/test/html/index.html` |
| Per-service exec | `services/<svc>/build/jacoco/test.exec` |

---

## Jenkins Publishing

The `Coverage Report` stage in each Jenkinsfile uses the **JaCoCo Jenkins plugin** (`jacoco()` step). It:

1. Reads all `.exec` files matching `services/**/build/jacoco/*.exec`
2. Locates compiled classes in `services/**/build/classes/java/main`
3. Locates sources in `services/**/src/main/java`
4. Publishes a coverage trend graph to the Jenkins build page
5. In stage pipeline: fails the build (`changeBuildStatus: true`) if line coverage < 40%

Prerequisites:
- The **JaCoCo Jenkins Plugin** must be installed in Jenkins (Manage Jenkins → Plugins → JaCoCo).
- The `aggregateCoverageReport` Gradle task must have run before the `jacoco()` step is invoked.

---

## SonarQube Integration

SonarQube also receives coverage data via the `sonar` Gradle task, which reads the JaCoCo XML report. The `sonarqube {}` block in each service's `build.gradle.kts` points `sonar.coverage.jacoco.xmlReportPaths` to the per-service report. The aggregate report is additionally published via `sonar.coverage.jacoco.xmlReportPaths` on the root project.

SonarQube quality gate thresholds are configured in the SonarQube server UI (project → Quality Gates). The pipeline will fail if the quality gate is not `OK` (controlled by the SonarQube Analysis stage, separate from the JaCoCo stage).

---

## Graduation Criteria

The 40% stage threshold was chosen as the initial baseline given the existing test suite from Taller 2. This should be reviewed and raised as the test suite matures:

| Milestone                | Recommended Threshold |
|--------------------------|-----------------------|
| Phase 6 completion       | 40%                   |
| After Phase 7 observability | 50%               |
| Before final presentation | 60%                  |

Update the `minimumLineCoverage` in `ci/Jenkinsfile.stage` when thresholds are raised.
