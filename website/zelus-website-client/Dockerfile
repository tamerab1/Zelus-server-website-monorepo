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

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -q -O /dev/null http://localhost/ || exit 1
