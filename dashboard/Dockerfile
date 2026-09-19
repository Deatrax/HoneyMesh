# ---- Build stage ----
# node:22 matches the toolchain this was developed and verified against
# (Vite 8 built successfully with it) — pinning avoids "worked yesterday"
# surprises from a floating `latest` tag.
FROM node:22-alpine AS build
WORKDIR /app
# Same layer-caching trick as the Java Dockerfiles: copy only the
# manifest first so `npm ci` is cached and skipped on source-only edits.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- Run stage ----
# The build stage's node_modules/toolchain never ship — the final image
# is just static files behind nginx, same "JRE + jar, not JDK + Maven"
# philosophy the backend Dockerfiles use.
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
