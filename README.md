# HoneyMesh — Walking Skeleton

This is not the finished project. It's the smallest version of the whole
topology that actually runs end-to-end in Docker, so the team spends Day 2
building real features on top of infrastructure that's already *proven* to
work, instead of debugging plumbing and business logic at the same time.

```
React dashboard → Load Balancer (add later) → Gateway (8080)
                                                 ├── decoy-service (8081)
                                                 ├── threat-engine-service (8082)
                                                 └── incident-service (8083)
                        Postgres (schemas: decoy / threat_engine / incident)
                        Redis
                        RabbitMQ
```

## 0. Tooling decisions already made for you

| Decision | Choice | Why |
|---|---|---|
| Java | 17 | Minimum for Spring Boot 3.x, safest LTS |
| Build tool | Maven | Matches your course's demo conventions |
| Spring Boot | **3.5.16** | Not 4.x. Spring Boot 4.0 only shipped Nov 2025 — it's ~9 months old, runs on a new Spring Framework major version, and has far less tutorial/StackOverflow coverage. 3.5.x is the most mature, best-documented line that exists, and it's what nearly every tutorial and course example assumes. Given zero margin for surprise migration issues in 2 days, boring wins. |
| Spring Cloud (Gateway) | 2025.0.0 (Northfields) | The release train built against Boot 3.5.x |
| Service discovery | None (static Docker DNS) | Eureka/Consul would be one more moving part to learn from scratch under this deadline. Docker Compose's built-in DNS resolves service names (`decoy-service`, `redis`, etc.) automatically — good enough for this project's scope. |

If you genuinely have time to spare later, Spring Boot 4 migration or adding
Eureka are both reasonable "stretch" additions — not now.

## 1. Prerequisites

- Docker + Docker Compose v2 (`docker compose version` should work)
- JDK 17 and Maven 3.9+ installed locally (for running a single service outside
  Docker during active development — see §4)

## 2. First run

```bash
cd honeymesh
docker compose up --build
```

First build will be slow (~3-5 min per service, downloading Maven Central).
Subsequent builds are much faster because of Docker layer caching (see §5).

You should see, in order: postgres/redis/rabbitmq become healthy, then the
three services start, then the gateway starts last (it `depends_on` the
others existing, though not on them being *healthy* — see §6 for why that
matters).

## 3. Verify the walking skeleton — do this before writing any real feature code

Each check below proves one load-bearing piece of the architecture actually
works. Do them in order; if one fails, the ones after it don't matter yet.

**a) Each service is up, directly:**
```bash
curl http://localhost:8081/api/decoy/ping
curl http://localhost:8082/api/threat/ping
curl http://localhost:8083/api/incidents/ping
```

**b) Gateway routes to each service (same URLs, through port 8080 now):**
```bash
curl http://localhost:8080/api/decoy/ping
curl http://localhost:8080/api/threat/ping
curl http://localhost:8080/api/incidents/ping
```

**c) The whole event pipeline — Decoy → RabbitMQ → Threat Engine → Redis — in one shot:**
```bash
curl -X POST "http://localhost:8080/api/decoy/test-event?decoyId=decoy-001&sourceIp=203.0.113.7"

# then, within a couple seconds:
curl http://localhost:8080/api/threat/last-event
curl http://localhost:8080/api/threat/count/decoy-001
```
`last-event` should echo back what you posted. `count` should increment by 1
every time you repeat the POST. If this works, RabbitMQ + Redis + the
cross-service JSON contract are all proven — this was flagged as your riskiest
infra piece, so it's worth confirming now, not on Day 2.

**d) WebSocket survives the trip through the Gateway** (needs a WS client —
`websocat`, `wscat`, or your browser devtools console):
```bash
# with websocat installed:
websocat ws://localhost:8080/ws/alerts
# or in a browser console:
# const ws = new WebSocket("ws://localhost:8080/ws/alerts"); ws.onmessage = e => console.log(e.data);
```
You should get a `{"type":"heartbeat",...}` message every 10 seconds. This is
the piece flagged as your highest debugging risk (WebSocket + cross-replica
fan-out) — proving the handshake alone works, through the actual gateway, de-risks
half of that problem before you've even added Redis Pub/Sub.

**e) RabbitMQ management UI:** http://localhost:15672 (user/pass:
`honeymesh` / `honeymesh_dev_pw`) — watch messages flow through the
`honeymesh.events` exchange in real time. Good for demos too.

**f) Health/Actuator on each service** (this is what the Gateway/load balancer
would use for failover routing once you wire that up):
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8080/actuator/health
```

If a–f all pass, the walking skeleton is done. Everything from here is real
feature work on a foundation you've already verified.

## 4. Recommended day-to-day dev loop (don't rebuild Docker images constantly)

Rebuilding a Docker image every time you change one line of Java is slow and
will burn your two days. Instead:

```bash
docker compose up -d postgres redis rabbitmq   # just the infra
```

Then run **your one service** directly from your IDE (IntelliJ Run button, or
`mvn spring-boot:run`) — every `application.yml` in this repo defaults to
`localhost` for Postgres/Redis/RabbitMQ hosts precisely so this works without
any config changes. Only rebuild the actual Docker image for that service
(and the gateway, if you touched routes) when you're integration-testing
against the whole stack or rehearsing the demo.

## 4b. Apple Silicon (M1/M2/M3) note

The runtime stage of every Dockerfile uses `eclipse-temurin:17-jre` (not the
`-alpine` variant). This is deliberate, not an oversight: `eclipse-temurin`'s
alpine tags have a long-standing history of only publishing `amd64` builds,
with no `arm64` variant — so on Apple Silicon, `docker compose up --build`
fails with `no match for platform in manifest`. The non-alpine tag is
multi-arch and works on both Intel and Apple Silicon Macs (and Linux/Windows).
Slightly bigger image, zero platform headaches — worth it for a 2-day project
across mixed laptops.

## 5. Why the Dockerfiles are written this way

```dockerfile
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline    # <- this layer is cached
COPY src ./src                       # <- only this invalidates the cache above
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine   # <- final image has no Maven, no source, just the jar
```
Copying `pom.xml` and downloading dependencies *before* copying `src/` means
Docker only re-downloads the internet when your dependencies change, not on
every code edit. The two-stage build means your final image is a JRE + a jar,
not a full JDK + Maven + your entire target folder. If you skip this pattern,
first-time Spring Boot + Docker builds get painfully slow, which matters a lot
with two days on the clock.

## 6. Known rough edges in this skeleton (intentionally deferred, not forgotten)

- **No load balancer container yet.** The design calls for an LB in front of
  the Gateway. Adding Nginx or Traefik in front of 2+ gateway replicas is a
  cheap addition later — don't build it until the rest works.
- **Docker networking is currently flat** (one bridge network). Splitting into
  an `edge` network (gateway only) and a `core` network (services + infra) is
  a ~10-line change to `docker-compose.yml` and strengthens your "Docker
  networking" requirement story with something real. Worth doing if you have
  a spare 20 minutes, not worth doing before the skeleton runs.
- **`ddl-auto: update`** is being used for speed. It's the right call for a
  2-day course project, but say so explicitly in your report as a deliberate
  trade-off (not Flyway/Liquibase) rather than letting it look like an
  oversight — matches how you've documented every other decision so far.
- **`depends_on` on the gateway is startup-order only**, not health-aware.
  Real health-aware routing (Gateway only sends traffic to healthy replicas)
  needs either a custom `HealthIndicator`-driven filter or a proper service
  registry. That's real Day 2 work for whoever owns the Gateway — the
  Actuator health endpoints are already exposed and ready to be read.
- **No JWT/auth yet** in incident-service (dependencies are pre-written,
  commented out, in its `pom.xml` — uncomment when you build it).
- **No RabbitMQ retry/DLQ** yet — this was already flagged as the riskiest,
  last piece to build. The exchange/queue naming here (`honeymesh.events`,
  `threat-engine.telemetry-queue`) is meant to make adding a dead-letter
  exchange later a config addition, not a redesign.
- **No cross-replica WebSocket fan-out** yet (Redis Pub/Sub). What exists now
  proves the handshake and single-instance broadcast work; the fan-out is
  additive on top of `AlertWebSocketHandler`, not a rewrite of it.

## 7. A deliberate non-obvious choice worth understanding: no shared Java classes between services

`decoy-service` and `threat-engine-service` each have their **own copy** of
`TelemetryEvent`, in their own package. This isn't duplication by accident —
it's the point. Microservices agree on a JSON *contract*, not on shared Java
class identity. If you're tempted to pull `TelemetryEvent` into a shared
library both services depend on, resist it: that reintroduces coupling
between services that are supposed to be independently deployable, and it's
exactly the "shared code = fake boundary" trap the microservices lecture
warns about.

The one place this matters technically: `threat-engine-service`'s
`Jackson2JsonMessageConverter` is configured with
`TypePrecedence.INFERRED`. Without that line, it would try to deserialize
incoming messages using the *producer's* class name
(`com.honeymesh.decoy.event.TelemetryEvent`), which doesn't exist in this
service's classpath, and every message would fail. `INFERRED` tells it to
trust the `@RabbitListener` method's declared parameter type instead. This is
the standard fix for this exact situation — keep it if you add more event
types.
