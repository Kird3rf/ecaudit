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

## After adding `TestSizeTrackedFileQueue` (7 tests)

Only `logger.SizeTrackedFileQueue` was targeted; the other three classes are unchanged.

- ecaudit tests run: 371 -> 378 (0 failures, 1 skipped)

| Metric (`logger.SizeTrackedFileQueue`) | Before | After |
|---|---:|---:|
| JaCoCo line coverage | 100.0% (13/13) | 100.0% (13/13) |
| JaCoCo branch coverage | 50.0% (1/2) | 100.0% (2/2) |
| PIT mutants generated | 6 | 6 |
| PIT mutants killed | 6 | 6 |
| PIT mutation score | 100.0% | 100.0% |

Note: the class was already fully covered (indirectly, via the rotating store-file
listener tests) before direct tests existed. The new tests add the uncovered `poll()`
on an empty queue branch and document that polling a file deleted after `offer()`
leaves its bytes in `accumulatedFileSize()` (`File.length()` is 0 for a missing file).

## After adding `TestStatementResourceAdapter` (25 tests)

Only `entry.factory.StatementResourceAdapter` was targeted. Tests parse real Cassandra 5.0.9
statements via `QueryProcessor.parseStatement` (raw) and `Raw.prepare(ClientState.forInternalCalls())`
(prepared), with no keyspace schema loaded.

- ecaudit tests run: 378 -> 403 (2 failures, 1 error, 1 skipped) — all three failures are
  production defects, left in place deliberately (see below)

| Metric (`entry.factory.StatementResourceAdapter`) | Before | After |
|---|---:|---:|
| JaCoCo line coverage | 1.0% (1/100) | 58.0% (58/100) |
| JaCoCo branch coverage | 0.0% (0/18) | 55.6% (10/18) |
| PIT mutants generated | 37 | 37 |
| PIT mutants killed | 0 | 26 |
| PIT mutants survived | 0 | 0 |
| PIT mutants with no coverage | 37 | 11 |
| PIT mutation score | 0.0% | 70.3% |

**26 of 37 mutants killed**, up from 0.

PIT measurement note: PIT 1.30.0 refuses to run on a red suite and runs a `@BeforeClass` test
class as one atomic unit, so `-DskipFailingTests=true` cannot skip individual methods. The PIT
numbers above were measured with the three defect-revealing tests temporarily `@Ignore`d in the
working tree only; that edit was reverted and is not committed.

The 11 remaining no-coverage mutants are all in branches that need a live schema (a materialized
view / index whose base table `View.findBaseTable` / `Schema.instance` can find — lines 159, 169-175,
193-209, 260, 282) plus the raw create-aggregate path (line 348) that currently throws (defect 2).

### Production defects revealed (not fixed)

1. `resolveBaseTableResource(AlterViewStatement.Raw)` and `resolveBaseTableResource(DropViewStatement.Raw)`:
   when the base table cannot be found they return `DataResource.keyspace(name.getName())`, i.e. a
   keyspace resource named after the *view* (`data/mv`), instead of `DataResource.keyspace(name.getKeyspace())`
   (`data/ks1`) as the prepared overloads and `DropIndexStatement.Raw` do.
   Failing tests: `testResolveBaseTableResourceFromAlterViewRawFallsBackToKeyspace`,
   `testResolveBaseTableResourceFromDropViewRawFallsBackToKeyspace`.
2. `resolveAggregateKeyspaceResource(CreateAggregateStatement.Raw)` reads a private field named `name`,
   but in Cassandra 5.0.9 `CreateAggregateStatement.Raw` calls it `aggregateName`, so the call throws
   `IllegalArgumentException: Cannot locate field name on class ...CreateAggregateStatement$Raw`.
   Failing test: `testResolveAggregateKeyspaceResourceFromCreateAggregateRaw`.

### Open question (not asserted)

`FieldUtils.readField` throws `IllegalArgumentException` for an *absent* field; the adapter only
catches `IllegalAccessException` and wraps it in `CassandraAuditException`. So an absent field
(defect 2 above, and `testResolveRoleResourceFailsWhenRoleFieldIsAbsent`) escapes unwrapped. The
test only asserts that a `RuntimeException` mentioning the field name is thrown; whether it should
be a `CassandraAuditException` is undecided.
