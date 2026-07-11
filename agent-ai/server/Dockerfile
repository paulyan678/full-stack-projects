FROM node:24-alpine
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY server/package.json server/package.json
RUN pnpm install --filter @agent-ai/server --prod --frozen-lockfile
COPY server/src server/src
USER node
EXPOSE 5001
CMD ["node", "server/src/server.js"]
