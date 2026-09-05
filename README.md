# Distributed URL shortener

Core v1 is in progress. **M1 (this checkout):** create + cache-first redirect against Postgres and Redis.

## Local (M1)

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
- Redirect: `GET /s/{code}` → 302
- Public metadata: `GET /api/v1/urls/{code}` (no click totals unless `publicClickCount` was set at create; M1 click count is always 0)

Custom aliases are stored and looked up **lowercase**. Random codes are 7-character Base62 and are **not** derived from database IDs.

## Privacy / randomness (documented fully as features land)

- **Short codes:** CSPRNG Base62, unique on `LOWER(short_code)`, 5 insert retries then HTTP 500 `short_code_exhausted`.
- **IP hashing:** not stored yet (click pipeline is M2). Planned: salted SHA-256 (`IP_HASH_SALT`).
- **SSRF / Safe Browsing:** protocol check only in M1; DNS SSRF + denylist file in M4; third-party reputation is a documented hook later.

## Deploy

Railway + Vercel steps are in `PLAN.md` milestone M9. Do not put real JWT/API tokens in git.
