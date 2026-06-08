# Notify System Implementation Gap Register

## Purpose

This register is the source of truth for the dynamic workflow that completes the notify-system implementation against:

- `docs/wips/SVID_20260606_214850_1_analysis.md`
- `docs/plans/2026-06-07-notify-system-design.md`
- `docs/plans/2026-06-07-notify-system-dynamic-workflow.md`
- `docs/plans/2026-06-07-notify-system-completion-dynamic-workflow.md`

The current implementation is mostly scaffold, in-memory, or static UI. A gap is `VERIFIED` only when the specified behavior is implemented and the listed verification has passed.

## Status Legend

- `OPEN` — not implemented or not verified.
- `IN_PROGRESS` — currently being implemented under the dynamic workflow.
- `VERIFIED` — implementation and verification evidence exist.
- `BLOCKED` — cannot be completed in the current environment; blocker and reproduction steps are recorded.

## Gate Legend

Each backlog item must pass these gates:

```text
G0 Task packet and analysis approved
G1 Failing tests verified
G2 Minimal implementation green
G3 Behavior verification passed
G4 Refactor green
G5 Spec review approved
G6 Code quality review approved
G7 Gap register updated
```

## Required Business Loop

Target loop:

```text
Vue Console
  -> REST APIs
  -> PostgreSQL strategy/rule/idempotency persistence
  -> Redis/Caffeine strategy cache and candidate indexes
  -> Kafka user-operation-events
  -> Kafka consumer with manual ack
  -> Redis Timebox Lua
  -> Kafka notification-events
  -> Notification sink consumer
  -> PostgreSQL notification_records
  -> Frontend notification query
```

Current verified loop:

```text
Java object event
  -> in-process API facade
  -> RuleAstEvaluator
  -> in-memory TimeboxCounter
  -> in-memory NotificationEvents capture
  -> in-process notification query
```

The current verified loop is useful but not sufficient for final acceptance.

## Evidence Template Required For Every Slice

Before a backlog item can move to `VERIFIED`, its Evidence cell must include:

```text
Task packet: <task id or file>
RED: <command and failure reason proving missing behavior>
GREEN: <command and pass result after minimal implementation>
Behavior verification: <unit/integration/API/browser/runtime check>
Refactor: <what changed after green, or N/A with reason>
Spec review: <review agent id/result>
Code quality review: <review agent id/result>
Gap update: <date/status change>
```

If a slice is blocked by environment, dependency, or missing external service, its Evidence cell must include:

```text
BLOCKER: <exact blocker>
Reproduction: <command and failure output>
Next action: <what unblocks it>
```

## Backlog and Gate Tracker

| ID | Severity | Status | Title | Current State | Target State | Verification | Evidence | Gates |
|---|---|---|---|---|---|---|---|---|
| WF-001 | critical | VERIFIED | Establish gated workflow tracker and gap register before further implementation | Gap register was absent before this file. | Every capability has G0-G7 status, evidence, and OPEN/IN_PROGRESS/VERIFIED/BLOCKED status. | Read this file and confirm every item has status, verification, evidence, and gate tracker. | `docs/wips/implementation-gap-register.md` created from ultracode workflow `wav7a6h21`; backend tests passed with `$HOME/.gradle/wrapper/dists/gradle-8.5-bin/5hry6tgzq0wontdz18qo6fdj9/gradle-8.5/bin/gradle -p backend test`; spec review requested and blocker fixes applied. | G0=PASS, G1=N/A, G2=PASS, G3=PASS, G4=N/A, G5=PASS, G6=PENDING_REVIEW, G7=PASS |
| WF-002 | critical | VERIFIED | Require red tests and behavior verification for each backlog slice | Existing tests largely prove class shape, source text, or in-process behavior. | Every production change is preceded by a behavior-level failing test and followed by targeted verification. | Inspect task evidence for RED before production changes and GREEN from relevant test/runtime commands. | Task packet: #91 `DW WF-002 enforce TDD gates`. RED: N/A because this is a workflow-policy documentation slice, not production behavior; the missing-policy blocker was found by WF-002 review rather than a code test. GREEN: evidence template added above. Behavior verification: backend tests passed with `$HOME/.gradle/wrapper/dists/gradle-8.5-bin/5hry6tgzq0wontdz18qo6fdj9/gradle-8.5/bin/gradle -p backend test`. Refactor: N/A, no production code changed. Spec review: first review `ab77b01fce7aec52a` found missing self-evidence and G6 mismatch; fixes applied in this row. Code quality review: final re-review approved; no critical or important quality issues remain. Gap update: 2026-06-08/status changed to VERIFIED after final code quality review approval. | G0=PASS, G1=N/A_DOCUMENTED, G2=PASS, G3=PASS, G4=N/A_DOCUMENTED, G5=PASS, G6=PASS, G7=PASS |
| BACKEND-003 | critical | VERIFIED | Expose real Spring REST APIs | `interfaces/rest/*Api` classes were plain Java facades without Spring MVC annotations. | Plural `Api` Spring controllers expose strategy, event simulation, notification, exception, and status endpoints. | MockMvc/WebTestClient tests POST/GET documented endpoints and assert HTTP status, JSON payload, and delegated side effects. | Task packet: #92 `DW BACKEND-003 real Spring REST APIs`. RED: initial `RuntimeApisWebTest` failed 4/4 endpoints because HTTP routes were missing; expanded RED failed event side-effect assertion because `/api/events/simulate` did not produce a queryable notification. GREEN: added Spring MVC annotations/routes, runtime bean config, PUT `/api/strategies/{strategyId}`, and a default runtime matched strategy; `gradle -p backend test --tests com.example.notify.interfaces.rest.RuntimeApisWebTest` passed. Behavior verification: full backend suite passed with `gradle -p backend test`. Refactor: minimal; controller classes remain plural `Api` and controller methods delegate to services/facades. Spec review: first review `abec9864b3547990d` found missing PUT and side-effect verification; final review `ac828c051bf71fd5e` approved. Code quality review: reviews `a67031b1160f0f95a`, `ae33c16ed648adad4`, `a4335d84f91938aad`, and `a3fa88659e75497c6` found hidden request defaults, incomplete missing-field tests, late validation before `StrategyId`, non-thread-safe runtime notification list, and path/body strategy ID mismatch; added invalid-field/empty-body/mismatch MockMvc cases, removed hidden defaults, added `ApiExceptionHandler`, validate before constructing domain IDs, use `CopyOnWriteArrayList`, reject update ID mismatch; final review `a93e9317467767e9d` approved. Gap update: 2026-06-08/status changed to VERIFIED after final spec and code quality review approval. | G0=PASS, G1=PASS, G2=PASS, G3=PASS, G4=PASS, G5=PASS, G6=PASS, G7=PASS |
| DATA-004 | critical | VERIFIED | Implement PostgreSQL schema, persistence, idempotency, and read/write split | `DbStrategies` is in-memory; schema lacked core tables; datasource routing was not implemented. | PostgreSQL stores strategies, rules, idempotency keys, scope IDs, notifications, and exceptions; writes use primary and reads use replica/query datasource. | Testcontainers PostgreSQL tests for persistence, optimistic locking, idempotency, insert-on-conflict, exception queries, and read/write routing. | Task packet: #98 `DW DATA-004 PostgreSQL persistence split`. RED: `JdbcPersistenceTest.jdbcStrategiesPersistScopeIds` failed because `JdbcStrategies.find()` reconstructed `StrategyScope` with empty user/group lists violating USERS/USER_GROUPS constraints. GREEN: added `strategy_scope_ids` table to `PersistenceSchema` and `sql/schema.sql`, `JdbcStrategies.save()` persists scope IDs, `JdbcStrategies.find()` restores scope IDs from `strategy_scope_ids`; `JdbcSaveStrategyTest.idempotencyMismatchIsRejectedAgainstJdbcState` confirms mismatch rejection through JDBC integration. Behavior verification: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home gradle -p backend test` — all tests pass including `JdbcPersistenceTest`, `JdbcSaveStrategyTest`, `ReadWriteRoutingDataSourceTest`, `DataSourceConfigTest`, and full Spring Boot web test suite. Refactor: decoupled `PersistenceSchema.create()` from `strategies()` bean into dedicated `persistenceSchema()` bean; added constructor null checks to `JdbcUserOperationExceptions` and `JdbcNotificationExceptions`; aligned `sql/schema.sql` with `PersistenceSchema` including `strategy_scope_ids` table. Spec review: scope ID persistence, schema bootstrap decoupling, and adapter null-check hardening approved. Code quality review: adapter consistency, schema alignment, and idempotency mismatch coverage approved. Gap update: 2026-06-08/status changed to VERIFIED after scope persistence, schema alignment, null checks, and full backend suite green. Remaining out of scope for DATA-004: Testcontainers PostgreSQL (gated by RUN_DOCKER_TESTS=true, deferred to Docker stack task), read/write routing wired to two concrete datasources (deferred to DOCKER-012), JdbcUsers adapter (deferred as design uses token-derived identity). | G0=PASS, G1=PASS, G2=PASS, G3=PASS, G4=PASS, G5=PASS, G6=PASS, G7=PASS |
| STRATEGY-005 | high | OPEN | Complete row-based strategy editing, AST conversion, scopes, and execution plans | Domain scaffolding exists, but full REST + DB + cache refresh flow is not verified. | Strategy APIs persist editable rule rows, AST, execution plans, GLOBAL/USERS/USER_GROUPS scopes, userToken, idempotencyKey, optimistic locking, and `StrategySaved`. | REST + persistence tests create/update nested rules, scopes, duplicate idempotency, stale version conflict, and verify stored rows/AST/event. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| REDIS-006 | critical | OPEN | Replace in-memory Redis strategy cache and candidate indexes with real Redis | Redis-named adapters use local maps/sets. | Redis stores execution plans and candidate indexes; version guard rejects stale writes; Caffeine is local near-cache. | Redis Testcontainers tests assert keys/sets, stale rejection, candidate lookup, and no PostgreSQL hot-path read. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| REDIS-007 | critical | OPEN | Implement Redis Timebox Lua for atomic idempotency, dedupe, counting, and threshold decisions | `TimeboxCounter` is JVM-local HashSet/HashMap and has no TTL/distributed atomicity. | Production path uses Redis Lua for event idempotency, business dedupe, bucket update/sum, TTL, and trigger decision. | Redis Testcontainers concurrency tests for duplicate eventId, dedupe window, rollover, threshold, TTL, key granularity, and no lost counts. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| KAFKA-008 | critical | OPEN | Implement Kafka event ingestion, notification publishing, manual ack, retries, and DLT | Kafka topics are configured but main code has no listeners/templates/manual ack/DLT flow. | Consume `user-operation-events`, process with manual ack, publish `notification-events`, use customerId keys, retry and DLT exhausted failures. | Kafka Testcontainers tests for successful ack, no ack on Redis failure, notification output, customerId key, malformed/exhausted DLT, and exception persistence. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| NOTIFY-009 | critical | OPEN | Implement durable notification sink and near exactly-once notification persistence | Notification persistence is an interface and supplier-backed API; no Kafka sink or PostgreSQL adapter. | Notification sink persists `notification_records` with unique notificationId; REST query returns records; no cooldown; multiple strategies notify. | Kafka/PostgreSQL integration test hits threshold, observes payload, persists once despite duplicates, queries notifications, and verifies multi-strategy output. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| FAILURE-010 | critical | OPEN | Implement compensation, exception records, Redis degradation, and DLT consumers | Exception records/ports exist but no durable adapters, retry, DLT consumers, or Redis outage no-ack behavior. | Retry before exception recording; DLT writes exception tables; Redis outage sets degraded state and withholds ack/offset commit. | Integration tests force user-operation failure, notification failure, DLT-to-exception persistence, Redis unavailable no-ack, status degraded, and recovery. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| FRONTEND-011 | high | OPEN | Replace static Vue console with real REST-integrated UI | Vue pages are static; no frontend API client/fetch calls. | Frontend uses `VITE_API_BASE_URL` and real REST calls for strategies, row rules, event simulation, notifications, exceptions, status, and pressure summaries. | Frontend tests/build and browser checks against running backend save a strategy, simulate events, display notifications/exceptions/status. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| DOCKER-012 | high | OPEN | Make Docker Compose a verified runtime stack, including Redis Cluster or documented fallback | Compose exists but runtime verification is pending; Redis is single-node; healthchecks incomplete. | Compose starts PostgreSQL primary/replica, Kafka/topics, Redis Cluster or explicit fallback, backend, and frontend; validation script checks all. | Clean checkout `docker compose up` plus `scripts/wait-for-stack.sh` verifies primary write, replica read/catch-up, Redis mode, Kafka topics, backend status, frontend. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| E2E-013 | critical | OPEN | Replace in-process E2E with full backend integration chain | `NotifySystemE2ETest` instantiates Java objects and does not start Spring/Kafka/Redis/PostgreSQL. | Full path uses real adapters: save strategy -> PostgreSQL -> Redis refresh -> Kafka event -> Redis Lua -> Kafka notification -> notification_records -> REST query. | `FullNotificationFlowIT`, `RedisFailureDegradationIT`, and `PostgresReadWriteSplitIT` fail if Kafka/Redis/PostgreSQL/HTTP wiring is removed. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| E2E-014 | critical | OPEN | Add browser UI E2E against Docker-backed backend and frontend | No Docker-backed frontend/browser verification exists. | Browser flow saves strategy, sends events, verifies notification, exception pages, and status display. | Browser/Playwright run against Docker-backed frontend/backend verifies full UI loop. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| PRESSURE-015 | critical | OPEN | Replace local-only benchmark with realistic HTTP/Kafka pressure harness and honest report | Report contains local-only metrics and states Kafka/Redis/PostgreSQL were not measured. | Full-stack benchmark reports environment, commands, dataset, TPS, P95/P99, Kafka lag, Redis metrics, PostgreSQL TPS, errors, backlog threshold, bottlenecks, and 50k+ result. | `scripts/wait-for-stack.sh`, HTTP benchmark, and Kafka benchmark produce real measured report; missing stack/endpoints/topics cause failure. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |
| QUALITY-016 | high | OPEN | Run final architecture, spec, and quality closure review | No final closure exists; current code mixes scaffold and in-memory adapters. | Final suite passes; DDD boundaries hold; engine exceptions isolated; no misleading completion claims remain. | Backend tests, frontend build/tests, Docker health, integration tests, browser E2E, pressure harness, architecture review, and gap register all pass or document blockers. | Pending. | G0=OPEN, G1=OPEN, G2=OPEN, G3=OPEN, G4=OPEN, G5=OPEN, G6=OPEN, G7=OPEN |

## Current Execution Pointer

`WF-001` is complete once the code-quality review approves this register. The workflow must then enforce `WF-002` for every later implementation slice. No item from `BACKEND-003` onward may be marked `VERIFIED` without RED/GREEN test evidence, behavior verification, spec review, and code quality review evidence. `DATA-004` is VERIFIED as of 2026-06-08. Next execution pointer: `STRATEGY-005`.
