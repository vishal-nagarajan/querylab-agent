# querylab-agent — architecture and decisions

> Phase 01 of QueryLab: an OSS Maven plugin + Spring Boot test-scope library that finds N+1s,
> lost indexes, and silent plan drift. v0.2 ships three modes; v0.3 adds probe mode (synthetic
> harness) and more rules.

Companion to `D:\claude-research\querylab\` (landing page, deployed to `jpa.utilhub.in`) and the
future `querylab-cloud` (Phase 02 backend).

---

## v0.2 architecture

Three execution modes, one report shape, one baseline.

```
                      target/queryreport/
                      ├── run.json   ← canonical record (also feeds future cloud upload)
                      └── index.html ← server-side rendered, embedded CSS, no client JS

mvn querylab:scan ────► BytecodeScanner    ────► RunReport (severity=WARN, mode=STATIC)
mvn querylab:explain ─► QueryExtractor + ExplainRunner + PlanAnalyzer ─► (severity=BAD,  mode=EXPLAIN)
mvn test ──────────────► QueryCaptureInspector + TestScopeListener     ─► (severity=BAD,  mode=RUNTIME)

mvn querylab:approve-baseline ─► copies run.json → .querylab/baseline.json
                                  Future runs → BaselineDiff highlights only NEW findings
```

All three modes feed `QueryLab.writeReport(report, outputDir, baselineFile)` which:
1. Writes `run.json` via `JsonReportWriter` (no JSON dependency — hand-rolled, schema is stable)
2. If baseline exists, computes `BaselineDiff` (added/removed flags + fingerprints)
3. Writes `index.html` via `HtmlReportWriter` (with optional diff banner at the top)

---

## Package layout

```
in.utilhub.querylab.
├── annotations/            Public API surface
│   ├── QuerylabIgnore      Skip a class or method from analysis (RUNTIME retention)
│   └── QuerylabExpect      Approve emission counts; repeatable
│
├── capture/                Runtime listener path (v0.1 origin, still active)
│   ├── QueryLab            Process-level singleton; recordEmission, recordExpectation, build, writeReport
│   ├── TestScope           ThreadLocal carrying current testName + ignored flag
│   ├── QueryCaptureInspector  Hibernate StatementInspector → QueryLab.recordEmission
│   └── TestScopeListener   JUnit Platform listener; reads @QuerylabIgnore + @QuerylabExpect from test methods
│
├── scan/                   Static-mode pipeline (Sprint A)
│   ├── BytecodeScanner     Two-pass orchestrator: catalog + detection
│   ├── EntityCatalog       @Entity classes + LAZY association fields (jakarta + javax persistence)
│   ├── RepositoryCatalog   Transitive JpaRepository / CrudRepository / ListPagingAndSorting…
│   ├── PatternMatcher      Loop detection + Pattern A (lazy access in loop) + Pattern B (repo call in loop)
│   └── Scope               .querylab/scope.yml reader; isClassIgnored / isMethodIgnored
│
├── explain/                Explain-mode pipeline (Sprint B)
│   ├── QueryExtractor      Walks compiled .class for @Query annotations
│   ├── ExtractedQuery      DTO — repository class, method, sql, nativeQuery
│   ├── DataSourceConfig    JDBC URL → dialect (POSTGRES, H2, MYSQL/MARIADB, ORACLE, UNKNOWN)
│   ├── AppConfigReader     Reads spring.datasource from application(-{profile}).yml; ${env:default} resolution
│   ├── ExplainRunner       Connects read-only, runs EXPLAIN with NULL-substituted placeholders
│   ├── PlanAnalyzer        Per-dialect regex parsers: Seq Scan / tableScan / type:ALL
│   └── LostIndexRule       Emits "lost_index" or "full_scan" flags at BAD severity
│
├── baseline/               Local diff (Sprint C)
│   ├── BaselineSnapshot    Set of approved fingerprint hashes + flag keys
│   ├── BaselineReader      Regex-based parse of our own run.json
│   └── BaselineDiff        Computes new-vs-approved, hides approved findings
│
├── rules/                  Rule engine + runtime rules
│   ├── Rule (interface)
│   ├── RuleEngine
│   └── NPlusOneRule        Runtime: emits when fingerprint count > threshold per test method
│
├── report/                 Output writers
│   ├── JsonReportWriter    Hand-rolled JSON of RunReport
│   └── HtmlReportWriter    Single-file HTML with embedded CSS + baseline diff banner
│
├── model/                  Plain DTOs
│   ├── Emission, Fingerprint, Flag, RunReport
│
├── autoconfig/             Spring Boot autoconfiguration
│   └── QuerylabAutoConfiguration  Registers QueryCaptureInspector via HibernatePropertiesCustomizer
│
└── maven/                  Maven Mojos
    ├── ScanMojo            mvn querylab:scan
    ├── ExplainMojo         mvn querylab:explain
    └── ApproveBaselineMojo mvn querylab:approve-baseline
```

---

## Suppression flow

```
@QuerylabIgnore on class/method
  ├── static scan  : Scope.isClassIgnored / isMethodIgnored skip the class/method entirely
  └── runtime      : TestScopeListener reflects on the test method, sets TestScope.Frame.ignored=true,
                     QueryLab.recordEmission drops the call

@QuerylabExpect(rule, fingerprintLike, emissions) on test method
  └── runtime      : TestScopeListener registers expectations for that testName
                     QueryLab.build runs rules → filters flags via isSuppressed():
                       - rule must match (or be empty)
                       - fingerprint SQL must contain fingerprintLike (or be empty)
                       - observed emission count must be ≤ emissions

.querylab/scope.yml
  └── static scan  : include/exclude package filtering, ignore_fingerprints (hash prefix match)
```

---

## Why each non-obvious choice

| Decision | Rationale |
|---|---|
| Hibernate `StatementInspector` over DataSource proxy (runtime mode) | Zero deps. Every SQL flows through it. We get the SQL text — enough for N+1 (count-based). Latency is v0.3 via a proxy. |
| JUnit Platform launcher TestExecutionListener (not Spring's) | Catches every JUnit 5 test — Spring or pure JUnit — without invasive `@ExtendWith`. ServiceLoader-discovered, zero user config. |
| Hand-rolled JSON serialization (no Jackson/Gson) | Avoids classpath conflicts with consumer's Jackson version. Model is tiny (5 types, all flat). The same hand-rolled approach reads the baseline back via targeted regex — locked by `BaselineReaderTest`. |
| Server-side HTML rendering in Java | Single static file output, no client JS, opens in any browser, ready for Phase 02 to upload as-is. Embedded CSS keeps it self-contained. |
| All consumer-facing deps (Hibernate, Spring Boot, JUnit Platform) marked `<optional>true</optional>` | Library doesn't impose versions on consumers. We compile against current floors; consumers run their own. |
| JDBC drivers (postgres, h2, mysql, mariadb) bundled into the plugin | Plugin classpath is isolated from the user's project — bundling is the cleanest way to make explain mode "Just Work" without consumers having to add drivers. ~10MB plugin jar; acceptable. |
| ASM Tree API (not Core visitor API) | Loop detection + multi-pass analysis is far cleaner with InsnList than visitor state machines. ~1.5s scan time on a 50k LOC project — fine. |
| Maven plugin packaging instead of multi-module | Single artifact ships both the runtime listener (via `<scope>test</scope>` dep) and the build-time goals (via `<plugin>`). Multi-module split lands when we have a Gradle plugin to add as a sibling. |
| Spring Boot 2.x compat via additional `META-INF/spring.factories` | Same jar covers both Spring Boot generations. Spring Boot 3 ignores spring.factories; 2.x ignores AutoConfiguration.imports. No conflict. |

---

## Test coverage

`mvn test` runs 32 unit tests covering the fragile pieces:

- `ExplainRunnerTest` (8) — placeholder substitution: `?`, `?N`, `:name`, Postgres `::cast`, quoted strings
- `DataSourceConfigTest` (6) — JDBC URL → dialect including MariaDB→MySQL aliasing
- `AppConfigReaderTest` (5) — `${ENV:default}` resolution, env-vs-system-property precedence
- `PlanAnalyzerTest` (5) — per-dialect plan parsing (PG Seq Scan, H2 tableScan, MySQL type:ALL)
- `BaselineReaderTest` (3) — regex parse of our own run.json schema
- `ScopeTest` (5) — glob matching for include/exclude

Plus the `examples/orders-sample` integration: every commit in CI runs all three modes against the sample and asserts the right flag fires.

---

## What's deferred

- **JPQL `@Query` planning** — needs Hibernate's SQM translator at plugin time. Scope-deferred to v0.3.
- **Latency capture** — DataSource proxy. v0.3.
- **Probe mode** (synthetic harness, no tests required, full BAD-grade evidence) — Sprints D–F, v0.3.
- **More rules**: plan-cache pollution, IN-clause explosion, slow-query regression, multi-bag fetch, transaction-boundary leak, lazy-load-outside-session, dialect drift. v0.3 / v0.4.
- **Multi-fork merge** — Surefire `forkCount > 1` produces one report per fork JVM today; need a merge step.
- **Maven Central distribution** — JitPack for v0.x; Central setup for v0.3 (1–2 days of Sonatype work).
- **Cloud baseline + PR diff app** — Phase 02.
- **MCP server for AI agents** — Phase 04.

---

## Sample app — `examples/orders-sample/`

Spring Boot 3 + JPA + H2. Contains:

1. **Pattern B for static scan + runtime mode**: planted N+1 in `OrderService.lineItems()` — `findIdsByOrderId` returns IDs (no L1 cache pre-population), then `.stream().map(id -> repo.findById(id))` in a lambda. Static scan catches the lambda body; runtime mode counts 25× emissions.

2. **Lost-index for explain mode**: `LineItemRepository.findByDescriptionNative` is a native `@Query` filtering on a non-indexed column. EXPLAIN reveals `tableScan`, the `lost_index` rule fires.

`OrderServiceTest#lineItems_planted_n_plus_one` is the runtime test that fires the flag in test mode.

This is the v0.2 acceptance bar: all three modes must light up the planted issues end-to-end before any release tag.

---

## Distribution

- v0.x → JitPack: `com.github.vishal-nagarajan:querylab-agent:<tag>`
- v0.3 / GA → Maven Central as `in.utilhub.jpa:querylab-maven` (1–2 days of Sonatype namespace + GPG setup)
