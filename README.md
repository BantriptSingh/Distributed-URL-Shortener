# Distributed URL shortener

Core v1 is in progress. **M4 (this checkout):** rate limits, SSRF, IP-hash and negative-cache tests.

## Local (M1–M2)

You do not need production secrets yet. Compose uses the placeholders in `.env.example`.

```bash
cp .env.example .env
docker compose up -d postgres redis
cd backend
./gradlew test
./gradlew bootRun
```

- API: http://localhost:8080
- Create: `POST /api/v1/urls` with `{"destinationUrl":"https://example.com"}` (alias `longUrl` accepted)
- Redirect: `GET /s/{code}` → 302, click logged via Redis Streams (`click-workers` group)
- Live pulses: `GET /api/v1/analytics/live` (SSE)
- Public metadata: `GET /api/v1/urls/{code}` includes `destinationUrl` unless the owner set `hidePreview`
- Auth: `POST /api/v1/auth/register` and `/login`; Swagger at http://localhost:8080/swagger-ui.html
- Generate real `JWT_SECRET` / `JWT_REFRESH_SECRET` / `UNLOCK_TOKEN_SECRET` / `CLAIM_TOKEN_SECRET` before production (`openssl rand -base64 48`). Local placeholders are in `.env.example`. Set `DEV_EXPOSE_RESET_TOKEN=false` when you are not debugging password reset.

Custom aliases are stored and looked up **lowercase**. Random codes are 7-character Base62 and are **not** derived from database IDs.

Custom aliases are stored and looked up **lowercase**. Random codes are 7-character Base62 and are **not** derived from database IDs.

## Privacy / randomness (documented fully as features land)

- **Short codes:** CSPRNG Base62, unique on `LOWER(short_code)`, 5 insert retries then HTTP 500 `short_code_exhausted`.
- **IP hashing:** click rows store salted SHA-256 (`IP_HASH_SALT`); the raw IP exists only ephemerally on the Redis stream. Local placeholder salt is in `.env.example` — generate a real salt before production (`openssl rand -base64 32`).
- **SSRF / reputation:** create-time http(s) only, DNS resolution (fail closed), reject loopback / RFC1918 / link-local / ULA / metadata IPs. Hostname denylist is `backend/src/main/resources/denylist.txt`. Google Safe Browsing is a documented hook (`UrlReputation.lookupSafeBrowsing`) and is not called yet.

## Deploy

Railway + Vercel steps are in `PLAN.md` milestone M9. Do not put real JWT/API tokens in git.
