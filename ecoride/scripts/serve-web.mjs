#!/usr/bin/env node
/**
 * Serves the exported web builds (single-page apps, so unknown paths fall back to index.html):
 *   rider app   http://localhost:9081   (apps/rider/dist,  npm run export:web -w @ecoride/rider-app)
 *   driver app  http://localhost:9082   (apps/driver/dist, npm run export:web -w @ecoride/driver-app)
 *   admin web   http://localhost:9083   (apps/admin/dist,  npm run build -w @ecoride/admin-web)
 * The apps call the API on the same host, port 9080.
 */
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const SITES = [
  { name: 'rider app', port: Number(process.env.RIDER_PORT ?? 9081), dir: join(root, 'apps/rider/dist') },
  { name: 'driver app', port: Number(process.env.DRIVER_PORT ?? 9082), dir: join(root, 'apps/driver/dist') },
  { name: 'admin web', port: Number(process.env.ADMIN_PORT ?? 9083), dir: join(root, 'apps/admin/dist') },
];
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.ttf': 'font/ttf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.map': 'application/json',
};

for (const site of SITES) {
  if (!existsSync(site.dir)) {
    console.log(`skip ${site.name}: ${site.dir} not built`);
    continue;
  }
  createServer((req, res) => {
    const path = decodeURIComponent((req.url ?? '/').split('?')[0]);
    let file = normalize(join(site.dir, path));
    if (!file.startsWith(site.dir) || !existsSync(file) || statSync(file).isDirectory()) {
      file = join(site.dir, 'index.html');
    }
    const type = TYPES[extname(file).toLowerCase()] ?? 'application/octet-stream';
    res.writeHead(200, {
      'Content-Type': type,
      'Cache-Control': type.startsWith('text/html') ? 'no-store' : 'public, max-age=3600',
    });
    createReadStream(file).pipe(res);
  }).listen(site.port, () => console.log(`${site.name}: http://localhost:${site.port}`));
}
