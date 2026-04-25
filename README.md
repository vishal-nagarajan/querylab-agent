# querylab-agent

[![JitPack](https://jitpack.io/v/vishal-nagarajan/querylab-agent.svg)](https://jitpack.io/#vishal-nagarajan/querylab-agent)

> **Find N+1s, lost indexes, and silent plan drift in your Spring Boot app — with or without integration tests.**

Three runnable surfaces, one report, one baseline.

| Mode | Command | Tests required? | DB? | Severity | Catches |
|---|---|---|---|---|---|
| **scan** | `mvn querylab:scan` | ❌ | ❌ | WARN | N+1 patterns from bytecode (lazy access in loops, repo calls in loops) |
| **explain** | `mvn querylab:explain` | ❌ | ✅ (read-only conn) | BAD | Lost indexes, full table scans (real EXPLAIN against your dev DB) |
| **runtime** | `mvn test` | ✅ | ✅ (whatever your tests use) | BAD | Real N+1 emissions observed during your test suite |

Part of [jpa.utilhub.in](https://jpa.utilhub.in).

---

## Install

### Maven

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<!-- For runtime mode (test-classpath observation): -->
<dependency>
  <groupId>com.github.vishal-nagarajan</groupId>
  <artifactId>querylab-agent</artifactId>
  <version>0.2.0</version>
  <scope>test</scope>
</dependency>

<!-- For scan / explain Maven goals: -->
<build>
  <plugins>
    <plugin>
      <groupId>com.github.vishal-nagarajan</groupId>
      <artifactId>querylab-agent</artifactId>
      <version>0.2.0</version>
    </plugin>
  </plugins>
</build>
```

### Gradle (Kotlin DSL)

```kotlin
repositories { maven("https://jitpack.io") }

dependencies {
    testImplementation("com.github.vishal-nagarajan:querylab-agent:0.2.0")
}

tasks.test { useJUnitPlatform() }
```

> Maven Central support is on the v0.3 plan. Until then, JitPack is the install path.

---

## Three modes

### 1. `mvn querylab:scan` — static, no DB, no tests

Walks your compiled classes with ASM. Catches:

- **N+1 from lazy access in loops** — getter on a `@OneToMany`/`@ManyToMany` LAZY field inside a `for`/`forEach`/`stream`
- **N+1 from repository calls in loops** — `findById` etc. called per iteration
- **Stream lambdas** — `list.stream().map(id -> repo.findById(id))` is detected by treating the lambda body as an implicit loop

Produces `target/queryreport/index.html` and `run.json`. WARN severity (structural prediction).

### 2. `mvn querylab:explain` — real EXPLAIN against your dev DB

Statically extracts every `@Query(value = "...", nativeQuery = true)` from compiled repositories, connects to your DB read-only, runs `EXPLAIN <sql>` (never `EXPLAIN ANALYZE`), and parses the plan.

Connection precedence:

1. `-Dquerylab.explain.url=...` / `.user=...` / `.password=...`
2. `application-{spring.profiles.active}.yml` → `spring.datasource.*`
3. `application.yml` → `spring.datasource.*`

Catches: full table scans, lost indexes (Seq Scan / tableScan / type:ALL with a filter present).

**Database support matrix:**

| DB | Dialect detection | EXPLAIN support | Driver bundled | Verified |
|---|---|---|---|---|
| Postgres | ✅ | ✅ | ✅ | ✅ |
| H2 | ✅ | ✅ | ✅ | ✅ |
| MySQL | ✅ | ✅ | ✅ | ⚠️ code-tested only |
| MariaDB | ✅ (alias of MySQL) | ✅ | ✅ | ⚠️ code-tested only |
| Oracle | ✅ | ❌ (v0.3) | ❌ | — |

JPQL `@Query` (without `nativeQuery=true`) is detected but **skipped in v0.2** — JPQL→SQL translation lands in v0.3. Native queries cover the most common lost-index cases.

### 3. `mvn test` — runtime mode (existing)

The library auto-registers itself via Spring Boot autoconfiguration when on the test classpath. Works with both Spring Boot 2.x (via `spring.factories`) and Spring Boot 3.x (via `AutoConfiguration.imports`). Hooks Hibernate's `StatementInspector` plus a JUnit Platform launcher listener — every SQL statement your tests emit is fingerprinted and counted per test method.

---

## Baseline diff — only see what's new on this branch

The PR-review feature. After your first clean run:

```bash
mvn querylab:approve-baseline
git add .querylab/baseline.json && git commit -m "chore: querylab baseline"
```

Subsequent runs diff against the committed `.querylab/baseline.json`. The HTML report shows:

- ✓ "no new flags vs. baseline" — when nothing drifted
- "+N new flags · +M new fingerprints" — when something did

Approved findings are hidden from the diff section but still visible in the full inventory.

---

## Escape hatches

When a flag is a known-acceptable case, suppress it. Two annotations + a config file.

### `@QuerylabIgnore` — skip a class or method entirely

```java
@Service
@QuerylabIgnore(reason = "Nightly batch job — N+1 here is intentional, runs once per day off-hours")
public class NightlyReportService { ... }
```

Or method-level:

```java
@Test
@QuerylabIgnore(reason = "Seeds 50k rows; emission count is the point of the test")
void seedFixturesForLoadTest() { ... }
```

Both static scan and runtime listener honour this — emissions during `@QuerylabIgnore` tests are dropped on the floor; classes/methods are skipped from analysis.

### `@QuerylabExpect` — declare an approved emission contract

```java
@Test
@QuerylabExpect(
    rule = "n_plus_one",
    fingerprintLike = "find_by_customer",
    emissions = 50,
    reason = "Pricing rules are per-customer; the per-customer fan-out is the contract"
)
void appliesPricingRules() { ... }
```

If the observed emission count for a matching fingerprint is ≤ `emissions`, the flag is suppressed. If it drifts above, the flag fires — drift detection.

`@QuerylabExpect` is repeatable on a single method.

### `.querylab/scope.yml` — project-wide config

```yaml
analyze:
  include:
    - "in.utilhub.payments.**"        # only these packages
    - "in.utilhub.orders.**"
  exclude:
    - "**.legacy.**"
    - "**.tests.**"

ignore_fingerprints:
  - "a3f2"                            # explicitly approved query (prefix match on hash)
  - "9c81"
```

Globs: `**` matches multiple segments, `*` matches one segment.

---

## Local dev

```bash
./mvnw verify

# Run all three modes against the bundled sample:
cd examples/orders-sample

../../mvnw querylab:scan                    # ~2s, WARN flag
../../mvnw test                              # ~10s, BAD flag (runtime)
../../mvnw querylab:explain \
  -Dquerylab.explain.url="jdbc:h2:mem:demo;INIT=CREATE TABLE IF NOT EXISTS line_item(id INT PRIMARY KEY AUTO_INCREMENT, order_id INT, description VARCHAR(255))"
                                             # ~3s, BAD flag (EXPLAIN)

open target/queryreport/index.html
```

---

## What's not in v0.2 (yet)

| | When | Note |
|---|---|---|
| **JPQL `@Query` planning** | v0.3 | Today only `nativeQuery = true` is fed through EXPLAIN. JPQL needs Hibernate Sqm translation. |
| **Latency / p95 capture** | v0.3 | Needs DataSource proxy. Currently we capture counts only. |
| **Probe mode** (synthetic harness, no tests required, full BAD-grade evidence including latency) | v0.3 | Runs `mvn querylab:probe` against ephemeral H2/Testcontainers or your dev DB. |
| **More rules** — plan-cache pollution, IN-clause explosion, slow-query regression, multi-bag fetch, transaction-boundary leak, lazy-load-outside-session, dialect drift | v0.3 / v0.4 | Some require runtime data (latency, plan cache); others land in scan. |
| **Cloud baseline + PR diff app** | Phase 02 | Same diff logic, just hosted; auto-comments on every PR. |
| **MCP server for AI agents** | Phase 04 | Expose the baseline to Claude Code / Cursor / Copilot. |

---

## Supported

- **Java 11+** bytecode (built on JDK 17)
- **Spring Boot 3.x** (autoconfig via imports file) — fully supported
- **Spring Boot 2.7** (autoconfig via spring.factories) — interfaces compatible, included
- **Hibernate 6.x** (compile target) — `StatementInspector` interface stable across 5.x/6.x
- **JUnit 5** / JUnit Platform — required for the runtime listener
- **Maven 3.6.3+** — required for the plugin

---

## License

Apache 2.0.
