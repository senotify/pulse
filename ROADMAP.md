# Pulse roadmap

Work proceeds top to bottom, one unchecked item at a time. Each item should land as 1-3 focused,
tested commits. Tick the box (`- [x]`) in the same commit that finishes the item, and add the date.

## Rules for every change

- `master` deploys to production automatically. Never push unless **all** tests pass:
  `cd backend && ./gradlew test` and `cd frontend && npm ci && npm run typecheck && npm test && npm run build`.
- Production runs on a shared 2 vCPU / 3.6 GB VPS. **Do not edit `deploy/docker-compose.prod.yml`**
  or add services, brokers or JVMs to production. New services go in the dev `docker-compose.yml`
  (behind a compose profile) and stay disabled in production until the owner turns them on.
- Keep the monolith working: anything extracted into a service must have a default in-process mode,
  and the service mode is opt-in through configuration.
- Flyway migrations are append-only (`V7__...`, `V8__...`). Never edit an applied migration.
- Follow the existing style: package-per-module, JdbcClient, records for DTOs, focused commits with
  conventional messages (`feat(...)`, `test: ...`, `docs: ...`). No new frameworks without a reason
  written in the commit body.
- Update `README.md` when behaviour or APIs change.

## Product

- [x] **Likes tab on profiles**: `GET /api/users/{username}/likes` (keyset paginated by like time), (done 2026-11-25)
      plus a "Likes" tab in the profile UI next to Media. Tests for ordering, blocks and pagination.
- [x] **Notifications, backend**: a `notifications` table filled by an outbox handler (likes,
      reposts, replies, follows, mentions), `GET /api/notifications`, `POST /api/notifications/read`
      and an unread count. Group duplicate likes per post. Tests. (done 2026-11-26)
- [x] **Notifications, frontend**: a Notifications page and nav item with an unread badge that
      polls every 60 s, marking items read when viewed. (done 2026-11-27)
- [x] **Rate limiting**: Redis fixed-window limits on login (per IP + username), registration,
      post creation and media upload, returning 429 problem details with `Retry-After`. Fail open
      if Redis is down. Tests with the in-memory test setup. (done 2026-11-28)

## Operability

- [ ] **Metrics**: Micrometer Prometheus registry, `/actuator/prometheus` (not exposed publicly:
      bind to a management port or restrict in security config), custom counters for posts
      created, fan-out writes and For You latency.
- [ ] **Structured logs**: JSON logging in the `prod` profile with request ids propagated through
      MDC; plain logs locally.

## Toward microservices (dev only, production unchanged)

- [ ] **Event contracts**: versioned JSON Schemas for every outbox event in `contracts/events/`,
      plus a test asserting each `OutboxWriter.append` payload validates against its schema.
- [ ] **Pluggable outbox publisher**: an `EventPublisher` interface with the current in-process
      dispatcher (default) and a Kafka implementation enabled by `pulse.events.transport=kafka`.
      Add Redpanda to the dev compose under a `services` profile. Unit-test the in-process path;
      the Kafka path compiles and is documented.
- [ ] **Gradle multi-project**: move the API to `backend/api`, add `backend/shared` for event
      records and contracts. No behaviour change; all tests still pass.
- [ ] **timeline-service**: a new Spring Boot app in `backend/timeline-service` that consumes
      post and follow events from Kafka and owns the Redis timelines, reusing the fan-out logic.
      The monolith keeps in-process fan-out unless `pulse.timeline.mode=remote`. Tests for the
      consumer logic without a broker.
- [ ] **recommendation-service skeleton**: extract the For You pipeline behind
      `GET /internal/for-you`; the monolith calls it through an HTTP client only when
      `pulse.recommendation.mode=remote`, with a timeout and in-process fallback.
- [ ] **Dev gateway**: an nginx config in the `services` compose profile routing
      `/api/timeline/**` to the services and everything else to the monolith.
- [ ] **Tracing**: OpenTelemetry instrumentation across the monolith and services in dev, with
      trace ids in logs.

## Documentation and performance

- [ ] **Architecture docs**: `docs/architecture.md` with diagrams of the monolith and the service
      split, plus ADRs in `docs/adr/` (monolith first, transactional outbox, hybrid fan-out,
      Snowflake ids, photos in PostgreSQL).
- [ ] **Load tests**: k6 scenarios in `loadtest/` (browse feeds, post, like) with a README
      explaining how to run them against the dev stack and how to read the results.
