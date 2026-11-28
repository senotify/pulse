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
PostgreSQL            Redis
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

The `For You` feed (`GET /api/timeline/for-you`) follows the stages of X's public `home-mixer` pipeline. Each stage is a separate class in `backend/.../recommendation/`:

```text
CandidateSource ×4 ──► merge by post ──► filter ──► FeatureHydrator ──► Ranker ──► FeedMixer ──► snapshot ──► page
 (≤200 each)            (union of        self,       7 features,        weighted   diversity +   Redis,
                         sources)        blocked,    O(1) queries       linear     network       30 min
                                         muted, seen                               balance
```

1. **Candidate sources**, each returning only original top-level posts:

   | Source | Retrieves | Reason shown |
   |--------|-----------|--------------|
   | `IN_NETWORK` | Posts from accounts you follow (last 3 days) | "From an account you follow" |
   | `ENGAGED_BY_FOLLOWING` | Posts liked or reposted by accounts you follow, from authors you don't follow | "Liked or reposted by @friend and 2 more you follow" |
   | `FOLLOWED_BY_FOLLOWING` | Posts from accounts your follows follow (second-degree graph) | "Followed by accounts you follow" |
   | `TRENDING` | The most-engaged posts of the last 24 hours; the cold-start source for new users | "Popular right now" |

   A post retrieved by several sources is merged and scored once. Its highest-priority source picks the reason.
2. **Filters**, applied before hydration so feature queries only cover posts that can be shown: the viewer's own posts, authors blocked in either direction, muted authors, and posts with an `IMPRESSION` event in the last 7 days.
3. **Feature hydration**, using four batched queries however many candidates there are. Every feature is normalized to [0, 1]:

   | Feature | Definition |
   |---------|------------|
   | `inNetwork` | 1 if you follow the author |
   | `authorAffinity` | Your replies (×2), reposts (×1.5), likes (×1) and clicks (×0.5) on the author in 30 days; a/(a+3) |
   | `socialProof` | Number of accounts you follow that engaged; 1 − 1/(1+n) |
   | `engagementVelocity` | (likes + 2·reposts + 1.5·replies) / (age_h + 2)^1.5; v/(v+1) |
   | `recency` | Exponential decay with a 6-hour half-life |
   | `authorReputation` | log(1+followers) / log(1+10 000), capped at 1 |
   | `contentAffinity` | Term overlap with posts you liked; a stand-in for embeddings until there is data |

4. **Ranking**: `WeightedRanker` computes score = Σ weight × feature, with weights in `application.yml`. It implements the `Ranker` interface, which is the seam for an ONNX Runtime model. Every page returns `modelVersion`, and clients echo it back in interaction events.
5. **Mixing**: greedy selection. Each post already picked from an author halves that author's next score, and out-of-network posts are capped at 50% while in-network candidates remain.
6. **Stable paging**: page 1 ranks once and stores the top 200 in Redis for 30 minutes. The cursor `<token>.<offset>` reads from that snapshot, so posts never repeat or go missing as scores drift. An expired snapshot, or Redis being down, means re-ranking rather than an error.

Add `explain=true` to get the feature vector with each item.

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
| 3 | Timeline module: following feed with Redis fan-out | **Done**: smoke-tested with real Redis (including a Redis outage) |
| 4 | Recommendation pipeline: For You feed, interaction log | **Done**: 29 tests pass in total, smoke-tested on PostgreSQL + Redis |
| 5 | React frontend | **Done**: typechecks, builds, served by nginx in compose |
| 6 | Production Compose, CI workflow, Caddy route | Next |

## Running locally

Prerequisites: Docker with Compose v2. Node.js 22+ is only needed for frontend development and the seed script.

```bash
# Everything: PostgreSQL, Redis, API, and the web app on http://localhost:3000
docker compose --profile api up --build
# 8080 busy? PULSE_API_PORT=8081 docker compose --profile api up --build

# Fill it with fictional demo users and posts (password for all: password123)
node scripts/seed.mjs

# Backend tests (H2 in PostgreSQL mode), no local JDK required
docker run --rm -v "$PWD/backend":/src -v pulse-gradle-cache:/home/gradle/.gradle \
  -w /src gradle:8.14-jdk21 gradle test --no-daemon

# Frontend: dev server with hot reload, proxying /api to the API
cd frontend && npm install && PULSE_API_TARGET=http://localhost:8081 npm run dev
npm run typecheck && npm test
```

With a local JDK 21, `cd backend && ./gradlew test` and `./gradlew bootRun` work too.

## Web app (step 5)

Vite + React 18 + TypeScript, with TanStack Query for server state and React Router. The UI follows a classic three-column microblog layout: navigation on the left, a bordered timeline in the middle, discovery on the right. It uses its own branding, a light theme and a navy "dim" dark theme. There's no UI framework, just one stylesheet of design tokens and inline SVG icons. The gzipped bundle is 77 KB.

- **Home**: a sticky header with **For you** and **Following** tabs; tap the active tab to refresh. For You shows each post's reason ("Liked or reposted by @raka"). The **Ranking signals** toggle reveals the seven features and the score behind every recommendation.
- **Posting**: an inline composer and a **Post** button that opens a compose modal (a floating button on mobile). The textarea grows as you type, and a progress ring counts down from 280.
- **Rich text**: #hashtags link to search, @mentions to profiles, and URLs open in a new tab.
- **Right sidebar**: search, **Trending on Pulse** (the hashtags used in the most posts in the last 24 hours) and **Who to follow** (accounts your follows follow, then the most-followed).
- **Explore**: post search, plus trends and suggestions on narrow screens.
- **Profiles**: a banner with an overlapping avatar, a "Follows you" badge, a Following button that turns into Unfollow on hover, mute, block with a confirm step, editing your name and bio, and tabs for posts, followers and following.
- **Notifications**: a nav item with an unread badge (polled every 60 s) opens a page of likes, reposts, replies, follows and mentions. Likes on one post are grouped ("Ana and 3 others liked your post"). Unread items stay highlighted while the page is open and are marked read as they load.
- **Threads**: the conversation above a reply, joined by a connector line, then the focused post with a full timestamp and stats, a reply box, and replies. Replies say "Replying to @user".
- **Profile photos**: click your own avatar to upload. The browser crops and resizes to 400×400; the server decodes and re-encodes to JPEG, which rejects non-images, checks dimensions before decoding (6000 px max, so decompression bombs fail cheaply) and strips EXIF/GPS metadata. Photos live in PostgreSQL and are served from `/api/avatars/{id}?v={key}` with a one-year immutable cache, where the key changes on every upload.
- **Feedback loop**: a post counts as an impression after 600 ms with at least half of it visible. Impressions and clicks are batched to `/api/interactions` with the feed surface, slot position and `modelVersion`, and flushed when the tab is hidden. These events drive the already-seen filter and click affinity in For You.
- **Production**: a multi-stage image builds the SPA and serves it from nginx. nginx also proxies `/api` to the API container, so the browser only ever talks to one origin.

Configuration (environment variables): `PULSE_DB_URL`, `PULSE_DB_USER`, `PULSE_DB_PASSWORD`, `PULSE_REDIS_HOST`, `PULSE_REDIS_PORT`, `PULSE_JWT_SECRET` (at least 32 bytes; always set it outside development), `PULSE_JWT_TTL` (ISO-8601 duration, default `PT24H`). Timeline tuning lives under `pulse.timeline` in `application.yml`.

## Following timeline (step 3)

```text
write:  POST /api/posts ──tx──► posts + outbox_events
                                     │  OutboxRelay (polls every 500 ms; at-least-once,
                                     ▼  exponential backoff, dead-letter after 10 attempts)
                               TimelineFanout ──► Redis ZADD tl:home:{follower}  (only if the key exists)

read:   GET /api/timeline/following
          Redis page ─┐
                      ├─ merge by id ─► render (blocks) ─► drop mutes and duplicate reposts ─► page
 high-reach pull ─────┘   (PostgreSQL)
```

- **Fan-out on write**, as in X's timeline service: each user's home timeline is a Redis sorted set of `postId:authorId` members scored by post id, capped at 800 entries.
- **Hybrid fan-out for high-reach accounts.** Authors with at least `fanout-threshold` followers (default 5000) are not pushed to followers. Their recent posts are pulled from PostgreSQL and merged on every read, so one post never triggers thousands of writes.
- **A timeline is complete or absent, never partial.** Fan-out writes go through a Lua script that skips missing keys. A sentinel member keeps empty timelines present. Timelines expire 7 days after their owner last read them, and the next read rebuilds them from PostgreSQL.
- **Redis is a cache, not a dependency.** If Redis is unreachable, the feed is served from PostgreSQL directly. If Redis loses its data, timelines rebuild on demand, so Redis runs with persistence off and LRU eviction.
- **Graph changes:** following someone backfills their 50 most recent posts. Unfollowing and blocking purge that author's entries. Mutes are applied at read time, so unmuting restores everything immediately.
- **Repost dedupe:** the original and its reposts show once, as the newest copy. Muting an author also hides reposts of their posts.

## API

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
| GET | `/api/users/{username}/likes` | Posts the user liked, most recently liked first; keyset cursor on (like time, post id); posts hidden by blocks are skipped |
| POST | `/api/posts` | `{content, replyToId?}`, at most 280 characters |
| GET / DELETE | `/api/posts/{id}` | Only the author can delete |
| GET | `/api/posts/{id}/replies` | Oldest first |
| GET | `/api/posts/{id}/ancestors` | The conversation above a reply, root first (recursive CTE, depth ≤ 50) |
| PUT / DELETE | `/api/me/avatar` | Requires auth; multipart `file` up to 5 MB (JPEG, PNG or GIF), normalized to a 400×400 JPEG |
| GET | `/api/avatars/{userId}?v={avatarKey}` | The profile photo, cacheable for a year |
| PUT / DELETE | `/api/posts/{id}/like` · `/repost` | Idempotent; acting on a repost targets its original |
| GET | `/api/timeline/following` | Requires auth; the viewer's own posts plus those of followed accounts, newest first, replies excluded |
| GET | `/api/timeline/for-you` | Requires auth; ranked recommendations with `source`, `reason`, `score` and `modelVersion`; `?explain=true` adds features |
| GET | `/api/discover/trends` | Top 5 hashtags by distinct posts in the last 24 hours (cached for 1 minute) |
| GET | `/api/discover/users?limit=3` | Requires auth; who to follow: second-degree accounts by number of mutual follows, then audience size |
| GET | `/api/search/posts?q=` | Case-insensitive substring search over post text (2-100 characters), paginated |
| GET | `/api/notifications` | Requires auth; likes, reposts, replies, follows and mentions, newest first, keyset paginated. Likes on one post are grouped (`actor` is the latest liker, `actorCount` the total) and move back to the top, unread, with each new like. Actors blocked in either direction are hidden |
| GET | `/api/notifications/unread-count` | Requires auth; `{count}` |
| POST | `/api/notifications/read` | Requires auth; marks everything read, or only `{ids: [...]}` when given; returns 204 |
| POST | `/api/interactions` | Requires auth; up to 100 `IMPRESSION`, `CLICK` or `DWELL` events with optional `surface`, `position`, `dwellMs` and `modelVersion`; returns 202 |

Rate limits (Redis fixed windows, configured under `pulse.ratelimit`; `PULSE_RATELIMIT_ENABLED=false` turns them off): login 10 per 15 minutes per client address and login name, registration 5 per hour per address, post creation 30 per 10 minutes per user, media uploads 40 per 10 minutes per user. Going over returns a 429 problem detail with a `Retry-After` header (seconds). The client address is the first `X-Forwarded-For` entry set by the proxies in front of the API. If Redis is unreachable the limits are skipped (fail open) and a warning is logged.

Blocks hide content in both directions and prevent follows, likes, replies and reposts. Mutes hide an author from the Following timeline. Every state change writes a domain event to `outbox_events` in the same transaction. Notifications are built from those events by an idempotent outbox handler (never for your own actions, and a reply that also mentions its recipient produces one notification).

## References

- [X Recommendation Algorithm repository](https://github.com/twitter/the-algorithm), especially [`home-mixer`](https://github.com/twitter/the-algorithm/tree/main/home-mixer)
- [X engineering description of the pipeline](https://blog.x.com/engineering/en_us/topics/open-source/2023/twitter-recommendation-algorithm)

Pulse is an independent implementation. It does not reuse source code from X's AGPL-3.0 project.
