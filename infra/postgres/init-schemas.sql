-- Runs once, automatically, the first time the postgres container initializes
-- an empty data volume (docker-entrypoint-initdb.d convention).
-- This gives each microservice its own schema in one shared Postgres instance,
-- preserving "no shared tables between services" without running 3 DB containers.

CREATE SCHEMA IF NOT EXISTS decoy;
CREATE SCHEMA IF NOT EXISTS threat_engine;
CREATE SCHEMA IF NOT EXISTS incident;
