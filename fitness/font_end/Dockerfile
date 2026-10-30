# syntax=docker/dockerfile:1
# Build 2 giai đoạn: Node build ra file tĩnh, nginx phục vụ file tĩnh + chuyển tiếp /api sang backend.

# ---------- 1. Build ----------
FROM node:20.18-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------- 2. Serve ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
