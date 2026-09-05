# Distributed URL Shortener — Implementation Plan

This plan is the source of truth for build order, scope cuts, and recorded assumptions. **Approved** (go, 2026-09-05) with A8 amendment and A29. Implementation proceeds milestone-by-milestone.

**Product:** REST short-link service (Java 21 / Spring Boot 3.x) + React/Vite frontend.  
**Deploy target:** Railway (API + Postgres + Redis) + Vercel (frontend).  
**Local:** Docker Compose (`backend`, `frontend`, `postgres`, `redis`).

Cut order if schedule slips: **§2.3 (v2) first → §2.2 (v1.1) second. Never cut §1 (correctness/security) or §2.1 (Core v1).**

---

## Recorded assumptions (senior calls — no blocking questions)

These are locked unless you override them before a milestone starts.

| ID | Topic | Decision |
|----|--------|----------|
| A1 | Password hashing | **Argon2id** via Spring Security `Argon2PasswordEncoder` (users, link passwords, API-key hashes use SHA-256 for keys because they are high-entropy). |
| A2 | IP privacy | **Salted SHA-256** of the remote IP (`IP_HASH_SALT` from env). Do **not** store raw IP. Geo is captured **at click time** from `CF-IPCountry` / `X-Geo-Country` / stub `"ZZ"`, then stored on the click row. Truncation is not used. |
| A3 | Rate-limit Redis outage | **Fail-open**: allow the request, emit a structured error-level log (`rate_limit_degraded=true`). Availability over strict enforcement when Redis is dead. |
| A4 | Public analytics | Column `urls.public_click_count` (boolean, default `false`). Public `GET /api/v1/urls/{code}` returns existence-safe metadata (code, createdAt, isActive, optional expiresAt) and **`clickCount` only if this flag is true**. Full analytics, SSE detail, breakdowns: **owner (JWT or API key with `read`) only**. |
| A5 | Landing-page live viz | Add **public, anonymized** SSE `GET /api/v1/analytics/live` that emits `{ "ts": "...", "clicks": n }` pulses (no codes, IPs, or URLs). Landing 3D subscribes here. If no traffic, frontend uses a seeded idle animation. |
| A6 | Password-protected links | **In Core v1**, because §2.1 redirect + PATCH already require it: `password_hash` on `urls`; `GET /s/{code}` returns **302 to `{FRONTEND_BASE_URL}/unlock/{code}`** (not the destination) when a password is set and no valid unlock token is present. `POST /api/v1/urls/{code}/unlock` verifies password and returns a **short-lived signed unlock token** (~5 min). Redirect then accepts `?u=` or cookie. §2.2 does **not** re-build this; v1.1 is bulk/UTM/webhooks/folders. |
| A7 | Soft vs hard delete | `DELETE /api/v1/urls/{code}` = **soft** (`is_active=false`, cache invalidate + negative-cache). `DELETE /api/v1/urls/{code}?hard=true` = **hard** (cascade clicks, guest claim rows). Owner only. |
| A8 | Custom alias | Becomes `short_code`. `custom_alias=true`. Validation: 3–30 chars, `[a-zA-Z0-9-]`, reserved: `api`, `admin`, `s`, `login`, `auth`, `swagger-ui`, `actuator`, `docs`, `static`. **Normalize to lowercase on create and lookup** (`Locale.ROOT`) so `MyLink` and `mylink` collide. Uniqueness is case-insensitive for **all** `short_code` values (`UNIQUE INDEX` on `LOWER(short_code)`); random codes are looked up case-insensitively too. |
| A9 | Short codes | **Cryptographic `SecureRandom` Base62, length 7**, unique index, **max 5 insert retries**, then `500` with a stable error code. Internal `urls.id` is Snowflake (index locality). **Never** encode Snowflake/time into the public code. |
| A10 | Negative cache | Redis key `url:miss:{code}` TTL **30s**. Positive cache `url:{code}` JSON TTL **24h**. Invalidate both on create/update/delete. |
| A11 | Click stream | Redis Stream `clicks`, consumer group `click-workers`. `XADD` with `MAXLEN ~ 100000` (approximate). Per-instance consumer name `click-{instanceId}` (`INSTANCE_ID` or random UUID at boot). `XACK` after successful DB insert. Scheduled `XAUTOCLAIM` (idle > 60s) for crash recovery. |
| A12 | maxClicks races | Redis `INCR` `url:clicks:{code}` **before** enqueue; if count would exceed `max_clicks`, return **410** and do not enqueue. Counter warmed on create/cache fill from `COUNT(*)` or stored total. |
| A13 | Guest claim | On guest create, return `claimToken` once. Store `guest_claim_tokens.token_hash` (SHA-256), expiry **7 days**. `POST /api/v1/urls/{code}/claim` with `{ "claimToken": "..." }` + authenticated user. |
| A14 | Password reset (v1) | No mail provider. `POST /forgot-password` always returns 204. **If `DEV_EXPOSE_RESET_TOKEN=true`**, login/forgot responses may include `devResetToken` labeled **DEV ONLY**. Production: `DEV_EXPOSE_RESET_TOKEN=false`; token only in structured logs at INFO in non-prod. |
| A15 | JWT | Access **15 min** (`JWT_SECRET`). Refresh **14 days** (`JWT_REFRESH_SECRET`), stored hashed, **rotated on use**, revoke on logout. |
| A16 | API keys | Raw form `sk_{env}_{random}` shown **once**. Store hash + 8-char prefix. Scopes: `read`, `write`. Header `X-API-Key` or `Authorization: Bearer sk_...` (detect prefix). |
| A17 | Geo / Safe Browsing later | Geo: interface `GeoResolver`; v1 `HeaderGeoResolver` runs **on the redirect/producer** (request headers still present) and **country is written into the XADD payload**. Consumers must not call `HeaderGeoResolver`. Safe browsing: `UrlReputation` + `classpath:denylist.txt`; document Google Safe Browsing hook in README. |
| A18 | SSRF | After URL parse (http/https only), **resolve DNS** (`InetAddress.getAllByName`), reject if any address is loopback, RFC1918, link-local, ULA (fc00::/7), IPv4-mapped private, or metadata (`169.254.169.254`, `fd00:ec2::254`). Fail closed on resolution failure. |
| A19 | CORS | `CORS_ALLOWED_ORIGINS` comma-separated. **Never** `*`. Credentials allowed for cookie unlock if used. |
| A20 | QR | Client-side (`qrcode`) **and** `GET /api/v1/urls/{code}/qr.png` (ZXing). Auth: public if the code exists and is active (QR is the short URL, not analytics). |
| A21 | Build | **Gradle Kotlin DSL**, Java **21**, Spring Boot **3.4.x**, single `backend` module. |
| A22 | Frontend routes | `/` landing (only R3F). `/shorten`, `/dashboard`, `/dashboard/:code`, `/account`, `/login`, `/register`, `/unlock/:code`. Nav always: **Shorten · Dashboard · Docs · Account**. Docs = `APP` link to `{VITE_API_BASE_URL}/swagger-ui.html`. Code-split: `three` / R3F **only** on `/`. |
| A23 | E2E | Playwright smoke: shorten → hit `/s/{code}` (API origin) → analytics on dashboard. Needs running stack (Compose in CI service or Playwright against `docker compose`). |
| A24 | Seed | `SEED_DEMO_DATA=true` runs idempotent seed (fixed short codes `demo01a`, etc.). **Off by default.** First Railway deploy: turn **on once**, then off. |
| A25 | Observability | `X-Request-Id` generated or honored; JSON logs include `requestId`. Actuator: health, info, prometheus (or metrics) for latency, `cache.hit`, `stream.lag` gauges. |
| A26 | Deploy secrets | **Never invent.** M9 **stops** until the owner supplies real Railway/Vercel/GitHub values. Placeholders only in `.env.example`. |
| A27 | Guest POST rate | 30/min per IP. Authenticated JWT create: 120/min per user. API key write: 300/min per key. Redirect: 600/min per IP. Auth endpoints: 10/min per IP. |
| A28 | Bot UA | Static list (curl, bot, spider, slurp, facebookexternalhit, etc.). Clicks still stored with `is_bot=true`. “Real” totals exclude bots; `botClicks` is separate. |
| A29 | Unlock rate limit | `POST /api/v1/urls/{code}/unlock` is **10 attempts/min per IP per code** (same tier as auth). Wired in **M4** with the rest of Redis rate limiting — password gates are Core v1 (A6). |
| A30 | Live SSE cap | Cap concurrent connections on public `GET /api/v1/analytics/live` (e.g. **500**) in **M4** rate-limiting work. Unauthenticated streaming must not be unbounded. Do not implement in M2. |
| A31 | Link preview | Public `GET /api/v1/urls/{code}` includes **`destinationUrl` by default**. Column `urls.hide_preview` (boolean, default `false`); owners set it via PATCH to omit the destination from public responses. Owners always see the destination. |

---

## Environment variables (placeholders only)

Copy `.env.example` → `.env`. **You** fill real values. Nothing secret belongs in git.

### Backend (Railway + local)

| Variable | Example placeholder | Where you get a real value |
|----------|---------------------|----------------------------|
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/shortener` | Local: Compose. Prod: Railway Postgres **Connect** URL (use JDBC form or Spring `SPRING_DATASOURCE_*`). |
| `SPRING_DATASOURCE_USERNAME` | `shortener` | Railway plugin / Compose user |
| `SPRING_DATASOURCE_PASSWORD` | `changeme` | Railway plugin password |
| `REDIS_URL` | `redis://redis:6379` | Railway Redis plugin URL |
| `JWT_SECRET` | `change-me-access-min-32-chars` | Generate: `openssl rand -base64 48` |
| `JWT_REFRESH_SECRET` | `change-me-refresh-min-32-chars` | Separate `openssl rand -base64 48` |
| `IP_HASH_SALT` | `change-me-ip-salt-min-16` | `openssl rand -base64 32` — **stable**; changing it orphans old hashes (OK, not used as FK) |
| `UNLOCK_TOKEN_SECRET` | `change-me-unlock-min-32` | `openssl rand -base64 32` |
| `CLAIM_TOKEN_SECRET` | `change-me-claim-min-32` | `openssl rand -base64 32` |
| `APP_BASE_URL` | `http://localhost:8080` | Public API origin, no trailing slash (short URLs + OpenAPI servers) |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | Unlock redirects; also listed in CORS |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated; add Vercel URL after first frontend deploy |
| `SEED_DEMO_DATA` | `false` | `true` only for demo/first prod fill |
| `DEV_EXPOSE_RESET_TOKEN` | `true` locally / `false` on Railway | Must be **false** in production |
| `INSTANCE_ID` | empty = random UUID | Optional; set per replica if you want stable consumer names |
| `SPRING_PROFILES_ACTIVE` | `local` / `prod` | Compose vs Railway |

Railway often injects `DATABASE_URL` as `postgres://`. App will parse that **or** document mapping to JDBC in README (implementation must accept both).

### Frontend (Vercel + local)

| Variable | Example | Where |
|----------|---------|--------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Railway public API URL in prod |

### CI/CD (GitHub **Actions secrets** — you create these; never commit)

| Secret | Where |
|--------|--------|
| `RAILWAY_TOKEN` | Railway → Account → Tokens |
| `RAILWAY_SERVICE` / project IDs | Railway project settings (as required by `railway up` / official GitHub Action) |
| `VERCEL_TOKEN` | Vercel → Settings → Tokens |
| `VERCEL_ORG_ID` / `VERCEL_PROJECT_ID` | `vercel link` output |
| `JWT_SECRET` etc. | Same as Railway env (Railway holds runtime secrets; GH secrets only for deploy auth) |

---

## Milestone map (required order = spec §8)

Each milestone: **runnable artifact + tests green + one-paragraph summary.** Stop and report: done / tested / deployed / next. Do not silently continue.

```
M1 Backend core
M2 Click pipeline (consumer groups) + analytics + SSE
M3 Auth + API keys
M4 Rate limit + URL safety + IP hashing
M5 Full backend tests
M6 Frontend shell + Shorten + Dashboard (no 3D)
M7 Landing 3D + reduced motion
M8 Compose + seed + GitHub Actions
M9 Railway + Vercel + live smoke (needs your secrets)
M10 v1.1 only after Core is live
```

---

### M1 — Backend core

**Goal:** Entities, Flyway migrations, Snowflake IDs, random Base62 codes, create/resolve/positive+negative cache.

**Includes**
- Gradle project, `application.yml`, Flyway for tables in §2.4 (`users` through `guest_claim_tokens`; password_hash on urls; `public_click_count`).
- `ShortCodeGenerator` (7-char Base62, SecureRandom, retry 5).
- `SnowflakeIdGenerator` for PKs.
- `POST /api/v1/urls` (guest OK): validate shape (not full SSRF yet — **stub validator**, full SSRF is M4), optional custom alias (**lowercased**, A8), expiresAt, maxClicks, tags; cache warm.
- `GET /s/{code}`: cache-first, 404/410, **no click persist yet** (enqueue stub or skip until M2 — redirect must still 302).
- `GET /api/v1/urls/{code}` public-safe fields.
- Redis cache adapter (positive JSON + miss key).

**Runnable:** `./gradlew bootRun` or API container + Compose postgres/redis; `POST` then `GET /s/{code}` 302.

**Tests (this milestone):** generator format/uniqueness retries; cache miss vs hit unit/slice; MVC create + 404. (SSRF/stream tests wait for M4/M2.)

**Not yet:** auth, rate limit, streams, frontend.

---

### M2 — Redis Streams pipeline + analytics + SSE

**Goal:** Correct click logging under **2+ replicas**.

**Includes**
- `XGROUP CREATE` (MKSTREAM) at boot; **treat BUSYGROUP as success** so Railway restarts do not fail.
- Geo: `HeaderGeoResolver` on the **redirect producer** only; country is part of the XADD body.
- `XADD` click payloads (shortCode, urlId, ua, referer, ip **raw only in stream**, hashed in consumer — raw IP must not hit Postgres). `MAXLEN ~ 100000`.
- `XREADGROUP`, persist `clicks`, `XACK`.
- `XAUTOCLAIM` sweep.
- Analytics query service: totals, bot vs real, groupBy day/week, device/browser/os, referrer, country.
- `GET /api/v1/urls/{code}/analytics` deferred to M3 (needs owner auth). Analytics **service** ships in M2.
- Public `GET /api/v1/analytics/live` SSE in M2. Connection cap is **A30 / M4**.
- Dual-consumer Testcontainers test: two `ClickConsumer` instances, N messages, **exactly-once processing** (`clicks.stream_id UNIQUE`).

**Schema add:** `clicks.stream_id VARCHAR UNIQUE` for idempotent `XCLAIM` redelivery.

**Runnable:** create → redirect → row in `clicks` within a few seconds.

---

### M3 — Auth + API keys + ownership APIs

**Goal:** Full §2.1 auth surface + keys + owner CRUD/analytics/SSE/claim/QR/summary.

**Includes**
- Register, login, refresh (rotate), logout, forgot/reset (A14), `GET /me`.
- API keys CRUD + scopes; last_used_at.
- Attach `owner_id` on authenticated create; claim endpoint.
- List/patch/delete URLs; analytics; SSE `/urls/{code}/events`; `GET /analytics/summary`.
- OpenAPI security schemes: bearer JWT + API key.
- Password set on create/patch; unlock endpoint (A6).
- QR PNG endpoint.

**Runnable:** register → create link → analytics 200; guest create → register → claim.

---

### M4 — Rate limiting + URL safety + IP hashing

**Goal:** All §1 remaining security corrections.

**Includes**
- Redis sliding-window limiter; 429 + `Retry-After`; fail-open (A3).
- Unlock brute-force limit (A29); live SSE connection cap (A30, e.g. 500).
- Protocol denylist; DNS SSRF; `denylist.txt`.
- IP hashing already in the M2 consumer; M4 adds tests that `ip_hash` is never the raw address.
- Negative-cache already in M1 — add tests that 404 is cached.

**Runnable:** `javascript:` rejected; private IP rejected; burst POST → 429.

---

### M5 — Full backend test suite

**Goal:** Spec §4 backend tests in one green `./gradlew test`.

**Must fail if §1 is broken**
- Two-consumer no-loss/no-dup.
- Short code not derivable from id (property: codes not monotonic with time).
- SSRF + protocol cases.
- IP column never equals raw IP (assert stored value is 64-hex SHA-256).
- Negative cache: second missing code does not query DB (spy/verify).
- Public analytics: no breakdown without owner; clickCount omitted unless `public_click_count`.
- Create → resolve → 302 → click appears.
- Custom alias conflict 409.
- Cache hit on second resolve.
- 429 after burst.

**Runnable:** CI-equivalent local `./gradlew test`.

---

### M6 — Frontend shell + Shorten + Dashboard (no 3D)

**Goal:** Fast 2D app on real API. **Do not import R3F/three.**

**Includes**
- Vite, TS, Tailwind, Framer Motion (light), React Router, Recharts.
- Global 4-item nav.
- Shorten: URL, advanced (alias, expiry, tags, maxClicks, public click count, password). Result: copy, client QR, guest claim prompt (`claimToken` in `localStorage`).
- Dashboard table + detail analytics + SSE.
- Account: login/register, API keys, password reset (dev token UI if present).
- Unlock page for password links.
- Vitest + RTL for form validation / auth headers.

**Runnable:** `npm run dev` against local API; full guest + user flows.

---

### M7 — Landing 3D (route-only)

**Goal:** `/` uses R3F; all other routes stay 2D (bundle check: dashboard chunk has no `three`).

**Includes**
- Point-cloud intensity from `/api/v1/analytics/live`.
- `prefers-reduced-motion` + WebGL capability → CSS fallback.
- CTA as real DOM overlay.
- **Skip** physics footer (optional polish — cut by default).

**Runnable:** `/` animates; `/dashboard` no WebGL canvas.

---

### M8 — Docker Compose, seed, GitHub Actions

**Goal:** `cp .env.example .env && docker compose up --build` is the happy path.

**Includes**
- Dockerfiles (backend JRE 21, frontend nginx or Vite preview).
- Compose healthchecks; CORS localhost.
- Seed job/`--seed` / `SEED_DEMO_DATA`.
- Actuator health for Compose/Railway.
- GitHub Actions: on push — backend test + frontend lint/test; Playwright smoke against Compose **if** feasible in GHA services (postgres, redis, or full compose). **Deploy jobs on `main` are present but `if` secrets exist** — must not leak placeholders.
- Structured logging + Micrometer already wired from earlier; verify.

**Runnable:** Compose E2E locally. CI green on push **without** deploy if Railway tokens absent.

---

### M9 — Deploy + live smoke

**Goal:** Public URLs + reported smoke test.

**You must supply (blocking):** Railway account, Vercel account, GitHub repo secrets listed above, generated JWT/IP salts.  
**This milestone does not start until those exist.** Agent will not fabricate tokens.

**Steps (after secrets)**
1. Local Compose smoke first.
2. Railway: Postgres + Redis + backend Dockerfile; set env; `SEED_DEMO_DATA=true` **once**.
3. Vercel: `VITE_API_BASE_URL`.
4. Patch `CORS_ALLOWED_ORIGINS`, redeploy API.
5. Enable GH deploy on `main`.
6. Live: shorten on frontend → open `/s/{code}` on API → dashboard click within seconds.
7. **Report:** live frontend URL, live API URL, smoke result.

**Deliverable of the whole project is this report, not just passing tests.**

---

### M10 — v1.1 fast-follow (only after M9)

Bulk CSV/JSON create, UTM builder on form, dashboard tag filters/folders UX, per-link webhook (threshold, 3 retries, fire-and-forget). Password/unlock already in Core (A6).

**v2 (do not build in this plan):** workspaces, admin moderation, MaxMind, custom domains.

---

## Checkpoint protocol

After **each** milestone:
1. Commit in a small logical chunk (clear message).
2. Tell the owner: **what changed, tests run, what’s deployed (usually nothing until M9), what’s next.**
3. Wait for go-ahead to start the next milestone unless they say “continue through M*N*”.

---

## Definition of done (Core v1) — checklist

- [ ] All §1 corrections implemented with tests that fail if removed
- [ ] §2.1 features work via Docker Compose
- [ ] Backend + frontend tests pass in GitHub Actions
- [ ] Public Railway API + Vercel frontend
- [ ] Live smoke §5.6 reported with real URLs
- [ ] README: local setup, IP hashing, random short codes, geo/Safe Browsing extension points

---

## One consolidated question (non-blocking for M1–M8)

M1–M8 can proceed on placeholders. **For M9 only:** do you already have a Railway project, a Vercel project, and a GitHub repo you want this pushed to? If yes, you will paste tokens into GitHub/Railway/Vercel dashboards (not into chat). If no, M9 becomes a documented manual runbook and we still complete Core locally + CI.

Reply **`go`** to accept this plan and start **M1**, or list assumption IDs to override.
