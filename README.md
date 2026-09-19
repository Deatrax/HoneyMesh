# HoneyMesh

A distributed cyber-deception and incident-response platform, built as a Web Architecture course semester project. Fake ("decoy") endpoints capture attacker interactions, a rule-based engine correlates and scores threats in real time, and an analyst dashboard surfaces auto-created incidents with live WebSocket alerts.

## Architecture

```
Browser
   │
   ▼
dashboard (nginx, :5173) ──proxies /api, /ws──▶ load-balancer (nginx, :8080)
                                                        │ round robin
                                        ┌───────────────┴───────────────┐
                                        ▼                               ▼
                                   gateway-1                       gateway-2
                                (Spring Cloud Gateway)      (Spring Cloud Gateway)
                                        │                               │
                    ┌───────────────────┼───────────────────────────────┘
                    ▼                   ▼                   ▼
             decoy-service    threat-engine-service   incident-service
                (:8081)              (:8082)               (:8083)
                    │                   │                   │
                    └─────────┬─────────┴─────────┬─────────┘
                               ▼                   ▼
                          PostgreSQL             Redis            RabbitMQ
                    (schemas: decoy /       (correlation,     (async event
                     threat_engine /         blocklist,        pipeline)
                     incident)               idempotency)
```

Two Compose networks — `edge` (dashboard, load-balancer, both gateways) and `core` (everything else, with the gateways bridging both) — not one flat bridge.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5.16, Java 17 |
| API Gateway | Spring Cloud Gateway 2025.0.0, 2 replicas |
| Load balancer / reverse proxy | nginx 1.27-alpine |
| Auth | Spring Security + jjwt 0.12.6 (JWT, HS256, RBAC) |
| Messaging | RabbitMQ 3.13 (topic exchange, durable queues) |
| Cache / correlation state | Redis 7 |
| Database | PostgreSQL 16 (one instance, one schema per service) |
| Frontend | React 19 + Vite 8 + React Router 7 |
| Containerization | Docker Compose v2 |

## Requirements coverage

The 3 hard requirements — **ReactJS frontend**, **Spring Boot backend**, **Docker deployment** — are all met, including the frontend, which is now containerized alongside everything else.

Of the 16 optional backend requirements, all are addressed to some degree: REST architecture, Authentication, API Gateway, Microservices, Stateless services, Scalability, Fault tolerance, Load balancing, Caching, Service communication, Database design, Docker networking, and Concurrency are all genuinely built and demonstrable. A few are honest, documented trade-offs rather than textbook-complete (no RabbitMQ DLQ/retry, no cross-replica WebSocket fan-out, no service discovery) — see the course-concept mapping doc for exactly which, why, and where in the code.

## Prerequisites

- Docker Desktop / Docker Engine + Compose v2
- Node.js 18+ and a JDK 17 + Maven (only needed for running a single service outside Docker during development)

## Quick start

```bash
git clone <this repo>
cd honeymesh
docker compose up --build
```

Wait for all 10 containers to settle, then:

```bash
bash scripts/seed-decoys.sh   # seeds 3 realistic decoys, logs in as admin automatically
bash scripts/verify.sh        # 34-check automated verification, ~1 minute
```

Open **http://localhost:5173** and log in.

| User | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN + ANALYST |
| `analyst` | `analyst123` | ANALYST only |

## Project structure

```
honeymesh/
├── docker-compose.yml
├── infra/
│   ├── nginx/load-balancer.conf
│   └── postgres/init-schemas.sql
├── gateway/                    # Spring Cloud Gateway (routing only)
├── decoy-service/              # honeypot + admin CRUD + forensics
├── threat-engine-service/      # correlation, scoring, blocklist
├── incident-service/           # auth, incident lifecycle, WebSocket
├── dashboard/                  # React SPA, served by nginx in prod
├── dummy-website/              # standalone demo target (host process, not Dockerized)
└── scripts/                    # seed-decoys.sh, verify.sh/.ps1, demo-redteam.sh
```

## Key endpoints

| Service | Endpoint | Notes |
|---|---|---|
| incident-service | `POST /api/auth/login` | Issues the JWT every other service trusts |
| decoy-service | `GET/POST/PATCH/DELETE /api/decoy/admin/**` | Admin CRUD; token-protected, ADMIN-only for mutations |
| decoy-service | `/**` (catch-all) | The honeypot itself — deliberately public, no auth |
| threat-engine-service | `GET /api/threat/**` | Read-only correlation/scoring/blocklist queries; token-protected |
| incident-service | `GET/POST/PATCH /api/incidents/**` | Incident lifecycle; admin-only for assign/unblock/perma-block |
| incident-service | `ws://.../ws/alerts` | Live incident push notifications |

## Testing

- `bash scripts/verify.sh` — 34 automated checks across health, routing, the full event pipeline, Postgres schemas, Redis, RabbitMQ, JWT/RBAC, and the end-to-end escalation-to-incident chain. `scripts/verify.ps1` is the PowerShell equivalent.
- `bash scripts/demo-redteam.sh` — a 5-phase narrated live red-team walkthrough for presentations (needs `seed-decoys.sh` run first; `nmap` optional).

## Documentation

Full technical documentation — architecture, per-component deep dives down to the line level, end-to-end sequence diagrams, and a course-concept mapping table with anticipated Q&A — lives alongside this README:

- `00-System-Overview.md` through `07-Course-Concept-Mapping-and-QA.md`
- `TESTING-INSTRUCTIONS.md` — post-fix verification checklist
- `HoneyMesh_Full_Backend_Walkthrough.md`, `HoneyMesh_Presentation_RunThrough.md`

## Known limitations

No RabbitMQ dead-letter queue or retry policy (a deterministic consumer failure would redeliver indefinitely), no cross-replica WebSocket fan-out (only one `incident-service` instance runs today, so this is currently invisible), and static Docker DNS instead of a service registry. All are deliberate, documented trade-offs for a course-project timeline, not oversights — see doc 8 for the full reasoning on each.
