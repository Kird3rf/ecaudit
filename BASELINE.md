# Test measurement baseline

Measurements taken on the `ecaudit` module before any new tests were written.

## Build

Command:

    mvn --batch-mode --activate-profiles github -pl ecaudit -am test jacoco:report

- Result: BUILD SUCCESS
- Wall-clock time: 1m 05s
- Tests run: 411 (common: 40, ecaudit: 371, 1 skipped), 0 failures, 0 errors
- Toolchain: OpenJDK 11.0.32, Apache Maven 3.6.3

## JaCoCo line coverage (before)

Source: `ecaudit/target/site/jacoco/jacoco.xml`

| Class | Lines covered | Lines missed | Line coverage | Branch coverage |
|---|---:|---:|---:|---:|
| `logger.SizeTrackedFileQueue` | 13 | 0 | 100.0% | 50.0% (1/2) |
| `logger.FileQueueBootstrapper` | 24 | 3 | 88.9% | 87.5% (7/8) |
| `auth.AuditPasswordAuthenticator` | 0 | 2 | 0.0% | n/a |
| `entry.factory.StatementResourceAdapter` | 1 | 99 | 1.0% | 0.0% (0/18) |

All classes are in package `com.ericsson.bss.cassandra.ecaudit`.

## PIT mutation testing (before)

PIT `org.pitest:pitest-maven` 1.30.0, mutators `DEFAULTS` + `EXPERIMENTAL_NAKED_RECEIVER`,
`targetClasses` limited to the four classes above.

Command:

    mvn --batch-mode --activate-profiles github -pl ecaudit -am test-compile org.pitest:pitest-maven:mutationCoverage

- Wall-clock time: 30s
- Report: `ecaudit/target/pit-reports/`

| Class | Mutants generated | Killed | Survived | No coverage | Mutation score |
|---|---:|---:|---:|---:|---:|
| `logger.SizeTrackedFileQueue` | 6 | 6 | 0 | 0 | 100.0% |
| `logger.FileQueueBootstrapper` | 12 | 10 | 2 | 0 | 83.3% |
| `auth.AuditPasswordAuthenticator` | 0 | 0 | 0 | 0 | n/a (no mutable code: constructor-only class) |
| `entry.factory.StatementResourceAdapter` | 37 | 0 | 0 | 37 | 0.0% |
| **Total** | **55** | **16** | **2** | **37** | **29.1%** |

PIT line coverage over the target classes: 38/140 (27%).
