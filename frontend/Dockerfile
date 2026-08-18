# LinkSentry web client — container image.
#
# Two stages so the runtime image carries no Node toolchain, no source, and no
# node_modules: only the built bundle and a static file server.

# ---------- Build ----------
# Matches the Node major used by .github/workflows/frontend-ci.yml.
FROM node:24-alpine AS build
WORKDIR /app

# Manifest and lockfile first, so the dependency layer stays cached across
# source-only edits. `npm ci` installs the lockfile exactly and fails if it has
# drifted from package.json; `npm install` would silently resolve new versions.
COPY package.json package-lock.json ./
RUN npm ci

# Only what the build reads. Copying explicit paths keeps a host node_modules or
# dist directory out of the build context's effect on the image.
COPY tsconfig.json vite.config.ts eslint.config.js index.html ./
COPY src ./src

# Vite inlines VITE_* variables into the bundle at build time, so the API origin
# is fixed here and not read at container start. It must therefore be an origin
# the *browser* can reach — the published host port, never the `backend` Compose
# service name, which resolves only inside the Compose network.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---------- Runtime ----------
FROM nginx:alpine AS runtime

COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html

# Never serve as root. Port 8080 rather than 80 because an unprivileged process
# cannot bind a port below 1024.
USER nginx

EXPOSE 8080

# Bypasses the image's docker-entrypoint.sh: it exists to run envsubst templating
# and chown as root, neither of which applies to this static, non-root image.
ENTRYPOINT ["nginx", "-g", "daemon off;"]
