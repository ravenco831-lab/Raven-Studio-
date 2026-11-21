FROM node:20-alpine

RUN apk add --no-cache ffmpeg tini \
    && addgroup -S studio \
    && adduser -S studio -G studio

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY server.js ./

RUN mkdir -p /app/generated /app/uploads /app/tmp && chown -R studio:studio /app

USER studio

ENV NODE_ENV=production
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "server.js"]
