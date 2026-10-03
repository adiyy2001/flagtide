FROM node:24.21.0-alpine AS build
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    CI=true
RUN npm install --global pnpm@12.8.1
WORKDIR /build
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY tsconfig.base.json ./
COPY libs/core libs/core
COPY apps/admin apps/admin
WORKDIR /build/apps/admin
RUN pnpm exec ng build admin --configuration=production

FROM nginxinc/nginx-unprivileged:1.30.5-alpine
COPY docker/admin.nginx.template /etc/nginx/templates/default.conf.template
COPY docker/admin-config.sh /docker-entrypoint.d/40-admin-config.sh
COPY --from=build /build/dist/apps/admin/browser/ /usr/share/nginx/html/
USER root
RUN chmod +x /docker-entrypoint.d/40-admin-config.sh \
    && touch /usr/share/nginx/html/config.json \
    && chown nginx:nginx /usr/share/nginx/html/config.json
USER nginx
ENV FLAGWIRE_FRAME_ANCESTORS="'self' http://127.0.0.1:14400 http://localhost:14400" \
    FLAGWIRE_ADMIN_API_URL=http://127.0.0.1:18081 \
    FLAGWIRE_ADMIN_PROJECT=demo \
    FLAGWIRE_ADMIN_KEYS={}
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=12 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
