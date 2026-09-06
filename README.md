# Distributed URL shortener

Core v1 locally: API (Spring Boot) + React app + Postgres + Redis.

## Happy path (Docker Compose)

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:8081
- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Set `SEED_DEMO_DATA=true` in `.env` once to create `demo01a`, `demo02b`, `demo03c`.

## Host-run (API + Vite)

```bash
cp .env.example .env
docker compose up -d postgres redis
cd backend && ./gradlew bootRun
cd frontend && cp .env.example .env && npm install && npm run dev
```

Frontend then uses http://localhost:5173 against the API on :8080.

## Tests

```bash
cd backend && ./gradlew test
cd frontend && npm test && npm run build
```

Playwright smoke (stack already up): `cd e2e && npm ci && npx playwright install chromium && npx playwright test`

## Behaviour

- Create: `POST /api/v1/urls` with `{"destinationUrl":"https://example.com"}`
- Redirect: `GET /s/{code}` → 302; clicks via Redis Streams (`click-workers`)
- Live pulses: `GET /api/v1/analytics/live` (SSE)
- Auth: register/login; owner analytics and SSE require JWT or API key
- Custom aliases are stored and looked up **lowercase**. Random codes are 7-character Base62 and are **not** derived from database IDs.

Generate real `JWT_SECRET` / `JWT_REFRESH_SECRET` / `UNLOCK_TOKEN_SECRET` / `CLAIM_TOKEN_SECRET` / `IP_HASH_SALT` before production (`openssl rand -base64 48`). Local placeholders are in `.env.example`. Set `DEV_EXPOSE_RESET_TOKEN=false` when you are not debugging password reset.

## Privacy / URL safety

- **Short codes:** CSPRNG Base62, unique on `LOWER(short_code)`, 5 insert retries then HTTP 500 `short_code_exhausted`.
- **IP hashing:** click rows store salted SHA-256 (`IP_HASH_SALT`); the raw IP exists only ephemerally on the Redis stream.
- **SSRF / reputation:** create-time http(s) only, DNS resolution (fail closed), reject loopback / RFC1918 / link-local / ULA / metadata IPs. Hostname denylist is `backend/src/main/resources/denylist.txt`. Google Safe Browsing is a documented hook (`UrlReputation.lookupSafeBrowsing`) and is not called yet.

## Deploy

Railway + Vercel is milestone **M9** and stays blocked until you supply real tokens. GitHub Actions runs tests on push; deploy jobs only run if `RAILWAY_TOKEN` and `VERCEL_TOKEN` secrets exist (no placeholders).
