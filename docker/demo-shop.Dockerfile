FROM node:24.21.0-alpine AS build
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    CYPRESS_INSTALL_BINARY=0 \
    CI=true
RUN npm install --global pnpm@12.8.1
WORKDIR /build
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY tsconfig.base.json ./
COPY libs/core libs/core
COPY libs/angular libs/angular
COPY apps/demo-shop apps/demo-shop
WORKDIR /build/apps/demo-shop
ARG BASE_HREF=/
RUN pnpm exec ng build demo-shop --configuration=production --base-href="$BASE_HREF"

FROM node:24.21.0-alpine
WORKDIR /app
COPY --from=build --chown=node:node /build/dist/apps/demo-shop/ /app/
USER node
ENV HOST=0.0.0.0 \
    PORT=8080 \
    FLAGTIDE_SHOP_SDK_KEY=fws_demo_dev_sdk_0000000000000 \
    FLAGTIDE_SHOP_STREAM_URL=ws://127.0.0.1:18082/sdk/v1/stream \
    FLAGTIDE_SHOP_SNAPSHOT_URL=http://127.0.0.1:18082
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=12 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
CMD ["node", "/app/server/server.mjs"]
