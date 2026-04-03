FROM node:24-alpine AS build
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY client/package.json client/package.json
RUN pnpm install --filter @agent-ai/client --frozen-lockfile
COPY client client
RUN pnpm --filter @agent-ai/client build

FROM nginx:1.29-alpine
COPY client/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/client/dist /usr/share/nginx/html
EXPOSE 80
