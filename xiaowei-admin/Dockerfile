FROM node:20-alpine AS build
WORKDIR /app
COPY xiaowei-admin/package.json xiaowei-admin/package-lock.json* ./
RUN npm install
COPY xiaowei-admin/ .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/admin-nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
