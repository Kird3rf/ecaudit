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

## After adding `TestStatementResourceAdapter` (23 tests)

Only `entry.factory.StatementResourceAdapter` was targeted. Tests parse real Cassandra 5.0.9
statements via `QueryProcessor.parseStatement` (raw) and `Raw.prepare(ClientState.forInternalCalls())`
(prepared), with no keyspace schema loaded.

- ecaudit tests run: 378 -> 401 (0 failures, 1 error, 1 skipped) — the one error is a production
  defect, left in place deliberately (see below)

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

PIT measurement note: PIT 1.30.0 refuses to run on a red suite and treats a `@BeforeClass` test
class as one atomic unit, so the PIT numbers above were measured with the defect-revealing test
temporarily `@Ignore`d in the working tree only; that edit was not committed.

The 11 remaining no-coverage mutants are: the "base table found" branches of the view/index
resolvers, which need a live schema (lines 159, 169-175, 193-209, 260, 282), and the raw
create-aggregate path (line 348), which currently throws (defect below).

### How the `.Raw` overloads are reached in production

`AuditEntryBuilderFactory.createEntryBuilderForUnpreparedStatement` calls `QueryProcessor.getStatement`
and only falls back to the `.Raw` overloads when that throws `InvalidRequestException`. For the
view/aggregate DDL statements, `Raw.prepare` does not consult the schema; the only thing it can throw
is `ClientState.getKeyspace()`'s `InvalidRequestException` when the CQL name is unqualified and no
`USE` keyspace is set. So the `.Raw` overloads are reached only for statements Cassandra itself
rejects, when auditing the ATTEMPT/FAILED entries from `AuditQueryHandler.parse`. In that case the
keyspace inside the `Raw` object is `null` (these `Raw` classes are not `QualifiedStatement`s, so the
factory never injects the client keyspace).

### Production defect revealed (not fixed)

`resolveAggregateKeyspaceResource(CreateAggregateStatement.Raw)` reads a private field named `name`,
but Cassandra 4.0/4.1/5.0 `CreateAggregateStatement.Raw` calls it `aggregateName`, so the call throws
`IllegalArgumentException: Cannot locate field name on class ...CreateAggregateStatement$Raw`.
Upstream this is caught by `AuditEntryBuilderFactory.createEntryBuilder(String, ClientState)`
(`catch (RuntimeException)`), logged at DEBUG, and the audit entry gets the default resource
`data` with `Permission.ALL` and known-operation=false instead of a function resource.
Failing test: `testResolveAggregateKeyspaceResourceFromCreateAggregateRaw` (uses the qualified name
`ks1.agg` and expects `functions/ks1`, mirroring the `CreateFunctionStatement.Raw` twin; the field
mismatch fails before the keyspace is looked at, so the assertion is independent of the
unqualified-name question below).

### Latent defect in an unreachable branch (not asserted)

`resolveBaseTableResource(AlterViewStatement.Raw)` and `(DropViewStatement.Raw)` fall back to
`DataResource.keyspace(name.getName())` (the *view* name) instead of `name.getKeyspace()`, unlike the
prepared overloads and `DropIndexStatement.Raw`. With a qualified name this branch is never reached in
production (qualified names always prepare, so the prepared overload is used). No test asserts it.

### Open questions (not asserted)

1. On the only reachable `.Raw` path (unqualified name, no `USE`), `name.getKeyspace()` is `null`,
   and `View.findBaseTable(null, view)` hits `assert keyspaceName != null` in `Schema.getView`: with
   `-ea` (Cassandra's default JVM options) an `AssertionError` escapes the audit hook, since the
   factory only catches `RuntimeException`. What the audit entry should contain for such a
   statement (keyspace unknown) is undecided, so no test asserts it.
2. `FieldUtils.readField` throws `IllegalArgumentException` for an *absent* field; the adapter only
   catches `IllegalAccessException` and wraps it in `CassandraAuditException`, so absent fields escape
   unwrapped. `testResolveRoleResourceFailsWhenRoleFieldIsAbsent` only asserts that a
   `RuntimeException` mentioning the field name is thrown; whether it should be a
   `CassandraAuditException` is undecided.
