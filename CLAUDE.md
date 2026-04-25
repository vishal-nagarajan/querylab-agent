# querylab-agent — architecture and decisions

> Phase 01 of QueryLab. OSS Spring Boot test-scope library. Captures SQL fingerprints during `mvn test`, writes a local HTML report, flags N+1s.

Companion to `D:\claude-research\querylab\` (the landing page) and the future `querylab-cloud` (Phase 02 backend).

## v0 scope

What this delivers, end-to-end:

1. Consumer adds one `<dependency>` in `<scope>test</scope>`.
2. Spring Boot autoconfiguration registers a Hibernate `StatementInspector`.
3. A JUnit Platform launcher `TestExecutionListener` brackets every test method, attaching a `TestScope` to the current thread.
4. Each SQL emission is hashed, attributed to the current scope, and counted.
5. After the test plan finishes, we build a `RunReport`, run rules over it (just one: N+1), serialize to `target/queryreport/run.json`, and render `target/queryreport/index.html`.

Single Maven module. Java 11 bytecode. No transitive deps in consumer's classpath beyond what they already have (all our needed-for-compile deps are `<optional>true</optional>`).

## Capture path

```
@SpringBootTest                                   src/main/java/in/utilhub/querylab/
   └─> SpringApplicationContext starts             ├── autoconfig/QuerylabAutoConfiguration.java   <- registers everything
        └─> HibernatePropertiesCustomizer
             └─> sets hibernate.session_factory.statement_inspector
                  └─> QueryCaptureInspector            ├── capture/QueryCaptureInspector.java
                                                       │   .inspect(sql) → TestScope.current().record(sql)

JUnit Platform Launcher
   └─> reads META-INF/services/...TestExecutionListener
        └─> TestScopeListener                          ├── capture/TestScopeListener.java
             .executionStarted(test) → TestScope.start(testName)
             .executionFinished(test) → TestScope.end()
             .testPlanExecutionFinished() → QueryLab.global().writeReport()

QueryLab (singleton)                                   ├── capture/QueryLab.java
   .recordEmission(scope, sql)                              .writeReport() builds RunReport, runs rules, writes JSON+HTML
```

## Why each choice

| Decision | Rationale |
|---|---|
| Hibernate `StatementInspector` over DataSource proxy | Zero deps. Every SQL flows through it. We get the SQL text and that's enough for N+1 (count-based). Latency comes in v0.2 via a proxy. |
| JUnit Platform launcher TestExecutionListener (not Spring's) | Catches every JUnit 5 test — Spring or pure JUnit — without invasive `@ExtendWith`. ServiceLoader-discovered, zero user config. |
| Hand-rolled JSON serialization (no Jackson) | Avoids classpath conflicts with consumer's Jackson version. The model is tiny (5 types, all flat). |
| Server-side HTML rendering in Java | Single static file output, no client JS, opens in any browser, ready for Phase 02 to upload as-is. |
| All optional deps (Hibernate, Spring Boot, JUnit Platform) | Library doesn't impose versions on consumers. We compile against current floors; consumers run their own. |
| Single Maven module | v0.1 doesn't need multi-module split. When we add a Maven plugin or non-Spring path, refactor. |

## What's deferred

- **Latency capture** — needs a `DataSource` proxy or a Hibernate `Statistics`-based hook. v0.2.
- **EXPLAIN plans** — per-dialect adapters with a sandboxed schema clone. v0.3.
- **The other 9 rules** (lost index, plan cache pollution, etc.) — rule pack expansion. v0.3–v0.5.
- **Multi-fork support** — Surefire `forkCount > 1` produces one report per fork JVM today. Will need a merge step.
- **Non-Spring path** — for now, Spring Boot autoconfiguration is the only auto-wiring path. Pure JUnit users must register the inspector manually.
- **Cloud upload** — Phase 02.
- **GitHub PR comment** — Phase 03.

## Distribution

v0 → JitPack (`com.github.vishal-nagarajan:querylab-agent:<tag>`). Maven Central once the API is stable.

## Sample app — `examples/orders-sample/`

Spring Boot 3 + JPA + H2. Contains a planted N+1 in `OrderService.lineItems()`:

```java
return order.getLineItemIds().stream()
    .map(id -> lineItemRepo.findById(id).orElseThrow().getDescription())
    .toList();   // N database calls for N items — the textbook N+1
```

The accompanying test fires it. After `mvn test`:

```
target/queryreport/index.html  →  shows the N+1 flag with route, test method, fingerprint
```

This is the v0 acceptance test. If the planted N+1 doesn't visibly fire in the report, we ship nothing.
