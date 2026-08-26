# ── Stage 1: Node build ───────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Cache node_modules layer separately
COPY package.json package-lock.json* ./
RUN npm install --prefer-offline

# Build-time env vars (injected by docker-compose build args)
ARG VITE_API_URL
ARG VITE_TURNSTILE_SITE_KEY

ENV VITE_API_URL=$VITE_API_URL \
    VITE_TURNSTILE_SITE_KEY=$VITE_TURNSTILE_SITE_KEY

COPY . .
RUN npm run build

# ── Stage 2: Nginx static server ──────────────────────────────────────────────
FROM nginx:1.25-alpine

LABEL org.opencontainers.image.title="Zelus Website"

# Custom Nginx config for SPA routing
COPY docker-nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets from Node stage
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

# 127.0.0.1, deliberately not "localhost": this container's /etc/hosts
# resolves localhost to ::1 first, but nginx's bare `listen 80;` in
# docker-nginx.conf only binds IPv4 (0.0.0.0:80) — BusyBox wget doesn't
# fall back to IPv4 after an IPv6 connection refusal, so the healthcheck
# always failed, and Swarm kills+reschedules a container 3 failed retries
# after start (~90s) exactly like a crash loop even though nginx itself
# was always healthy. Confirmed directly: `wget http://localhost/` fails
# with "Connection refused" while `wget http://127.0.0.1/` succeeds, on
# the identical running container.
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
