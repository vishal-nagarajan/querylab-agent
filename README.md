# querylab-agent

> Your Spring app's SQL inventory, kept honest on every PR.

A Spring Boot test-scope library that captures every SQL fingerprint your tests emit, flags N+1s, and writes a local HTML report. Free, no account, runs offline.

Part of [jpa.utilhub.in](https://jpa.utilhub.in).

## Install

```xml
<dependency>
  <groupId>in.utilhub</groupId>
  <artifactId>querylab-agent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

(For now via JitPack — see *Distribution* below. Maven Central once 0.1.0 is tagged.)

## Use

```bash
mvn test
```

That's it. The agent registers a Hibernate `StatementInspector` automatically through Spring Boot autoconfiguration. After the test run finishes, look at:

```
target/queryreport/index.html
target/queryreport/run.json
```

## What it captures (v0)

- Every distinct SQL statement executed during a `@SpringBootTest` / `@DataJpaTest` run
- Per emission: SQL template hash, the test method that fired it, emission count
- A single rule: **N+1 detection** — same fingerprint emitted N× inside one test method (default threshold: 5)

## What it doesn't capture (yet)

| | Status |
|---|---|
| Latency (p95) | v0.2 — needs DataSource proxy |
| EXPLAIN plans | v0.3 — per-dialect adapters |
| Plan-cache pollution / IN-clause explosion | v0.3 — rule pack expansion |
| Baseline diff vs `main` | Phase 02 (cloud) |
| GitHub PR comment | Phase 03 |
| MCP server for AI agents | Phase 04 |

## Supported

- Java **11+** bytecode
- Spring Boot **3.x** (auto-config); manual config available for non-Spring tests
- Hibernate **6.x** (compile target); 5.x compatible at runtime
- Dialects: Postgres, MySQL, MariaDB, Oracle, H2

## Local build

```bash
./mvnw verify
cd examples/orders-sample
../../mvnw test
open target/queryreport/index.html  # the planted N+1 lights up
```

## Distribution

v0 ships via JitPack. Add to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

Then use the dep coordinates above.

## License

Apache 2.0.
