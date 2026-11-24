# Pulse

A Twitter-like portfolio platform built with Java 21, Spring Boot, PostgreSQL, Redis, Kafka-compatible events, and React. It demonstrates a production-minded home-timeline recommendation system inspired by the public design of X's [`the-algorithm`](https://github.com/twitter/the-algorithm), without copying its AGPL-3.0 source code.

## First-release architecture

Pulse uses a **modular monolith with explicit service boundaries** for the first release. It runs on the existing 2-vCPU, 4-GB Tencent Lighthouse VPS, while retaining a clean path to independently deployed microservices.

```text
React SPA
    │ HTTPS
Caddy edge proxy
    │
Pulse API (Spring Boot)
 ├── Identity module       users, sessions, profiles
 ├── Social graph module   follows, blocks, mutes
 ├── Post module           posts, replies, likes, reposts
 ├── Timeline module       following feed and fan-out delivery
 ├── Recommendation module candidate sources → features → ranking → filtering → mixing
 └── Interaction module    impressions, clicks, likes, reposts, dwell time
    │                  │
PostgreSQL            Redis (from step 3)
source of truth       caching, timelines, rate limits, ephemeral feature aggregates
```

A transactional outbox records domain events. In the scale-out topology, Debezium or an outbox relay publishes the same events to Kafka and each bounded context becomes its own Spring Boot service.

### Scale-out topology

```text
Gateway ───── Identity Service ─── PostgreSQL shard
       ├───── Social Graph Service ─── PostgreSQL / graph store
       ├───── Post Service ─── ScyllaDB + OpenSearch
       ├───── Timeline Service ─── Redis Cluster
       ├───── Home Mixer Service
       │        ├── Candidate services: in-network, graph, similarity, trending
       │        ├── Feature Store: Redis / online aggregates
       │        └── ONNX ranker
       └───── Interaction Service ─── Kafka → Flink → lakehouse / feature training
```

## Recommendation design

The `For You` feed follows the public pipeline described in X's `home-mixer` documentation:

1. **Candidate generation**: followed authors, second-degree graph authors, and trending posts.
2. **Feature hydration**: affinity, author reputation, recency, engagement velocity, content affinity, and prior-seen state.
3. **Ranking**: a transparent weighted ranker initially. Its `Ranker` interface supports replacing it with a Java ONNX Runtime model later.
4. **Safety and quality filters**: self, blocked/muted authors, duplicate candidates, and already-seen posts.
5. **Mixing and diversity**: cap consecutive posts from an author, balance in-network and out-of-network posts, and cursor pagination.

Real ML training is intentionally deferred until Pulse collects interaction events. The event schema, feature interface, model version fields, and ONNX ranker seam are included now. Building a model without representative behavioral data would only create synthetic accuracy claims.

## Why not deploy every microservice now?

The current production host has 2 vCPUs and 4 GB RAM and already hosts multiple sites. PostgreSQL, Redis, Kafka, OpenSearch, several JVMs, and observability storage cannot run reliably together there. The compact deployment runs one JVM, PostgreSQL, and Redis. CI builds images externally; the VPS only pulls and starts them.

## Repository layout

```text
pulse/
├── backend/                Spring Boot API and bounded modules
├── frontend/               React single-page app
├── docs/                   API and scale-out architecture
├── deploy/                 compact production Compose deployment
├── loadtest/               k6 scenarios
└── .github/workflows/      CI build, test, image publishing and opt-in deploy
```

## Status

| Step | Scope | State |
|------|-------|-------|
| 1 | Toolchain | Done (builds run in a `gradle:8.14-jdk21` container; no local JDK needed) |
| 2 | Identity, social graph, posts, outbox, Flyway schema | **Done**: tests pass on H2, smoke-tested on PostgreSQL 17 |
| 3 | Timeline module: following feed with Redis fan-out | Next |
| 4 | Recommendation pipeline: For You feed with weighted ranker | Planned |
| 5 | React frontend | Planned |
| 6 | Production Compose, CI workflow, Caddy route | Planned |

## Running locally

Prerequisites: Docker with Compose v2 (Node.js 22+ for the frontend, later).

```bash
# Tests (H2 in PostgreSQL mode), no local JDK required
docker run --rm -v "$PWD/backend":/src -v pulse-gradle-cache:/home/gradle/.gradle \
  -w /src gradle:8.14-jdk21 gradle test --no-daemon

# PostgreSQL + API on http://localhost:8080 (Swagger UI at /swagger-ui.html)
docker compose --profile api up --build
# 8080 busy? PULSE_API_PORT=8081 docker compose --profile api up --build
```

With a local JDK 21, `cd backend && ./gradlew test` and `./gradlew bootRun` work too.

Configuration (environment variables): `PULSE_DB_URL`, `PULSE_DB_USER`, `PULSE_DB_PASSWORD`, `PULSE_JWT_SECRET` (at least 32 bytes; always set it outside development), `PULSE_JWT_TTL` (ISO-8601 duration, default `PT24H`).

## API (step 2)

Auth is a stateless HS256 bearer JWT returned by register/login. GETs work anonymously; personalised fields such as `likedByMe` are false for anonymous callers. Lists use keyset pagination: pass `nextCursor` back as `?cursor=`, with `?limit=` from 1 to 50 (default 20). Errors are RFC 9457 problem details.

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/auth/register` | `{username, email, displayName, password}` → token + user |
| POST | `/api/auth/login` | `{login, password}`; `login` is a username or email |
| GET / PATCH | `/api/me` | Own account; PATCH `{displayName?, bio?}` |
| GET | `/api/users/{username}` | Profile with counts and relationship flags |
| PUT / DELETE | `/api/users/{username}/follow` · `/block` · `/mute` | Idempotent; blocking removes follows in both directions |
| GET | `/api/users/{username}/followers` · `/following` | Paginated |
| GET | `/api/users/{username}/posts` | Posts and reposts, newest first |
| POST | `/api/posts` | `{content, replyToId?}`, at most 280 characters |
| GET / DELETE | `/api/posts/{id}` | Only the author can delete |
| GET | `/api/posts/{id}/replies` | Oldest first |
| PUT / DELETE | `/api/posts/{id}/like` · `/repost` | Idempotent; acting on a repost targets its original |

Blocks hide content in both directions and prevent follows, likes, replies and reposts. Mutes are stored now and take effect when timelines arrive in step 3. Every state change writes a domain event to `outbox_events` in the same transaction.

## References

- [X Recommendation Algorithm repository](https://github.com/twitter/the-algorithm), especially [`home-mixer`](https://github.com/twitter/the-algorithm/tree/main/home-mixer)
- [X engineering description of the pipeline](https://blog.x.com/engineering/en_us/topics/open-source/2023/twitter-recommendation-algorithm)

Pulse is an independent implementation. It does not reuse source code from X's AGPL-3.0 project.
