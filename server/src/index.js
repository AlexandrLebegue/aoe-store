import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import { CatalogStore } from './store.js';
import { parseMultipart } from './multipart.js';
import { HttpError, sendJson, sendError, methodNotAllowed } from './errors.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '../..');
const DEFAULT_DATA_DIR = path.join(REPO_ROOT, 'data');
const ASSETS_DIR = path.join(HERE, '..', 'assets');

const MIME_TYPES = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.json': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.apk': 'application/vnd.android.package-archive',
};

function decodeSegment(value) {
  try {
    return decodeURIComponent(value);
  } catch {
    throw new HttpError(400, 'invalid_path', 'Chemin encodé invalide');
  }
}

async function serveStatic(req, res, store, pathname) {
  let relative;
  try {
    relative = decodeURIComponent(pathname.slice('/files/'.length));
  } catch {
    throw new HttpError(400, 'invalid_path', 'Chemin encodé invalide');
  }
  if (!relative || relative.includes('\0')) {
    throw new HttpError(404, 'file_not_found', 'Fichier introuvable');
  }
  const normalized = path.posix.normalize(relative);
  if (normalized.startsWith('..') || normalized.startsWith('/') || path.isAbsolute(normalized)) {
    throw new HttpError(404, 'file_not_found', 'Fichier introuvable');
  }
  const root = path.resolve(store.blobsDir);
  const absolute = path.resolve(root, normalized);
  if (absolute !== root && !absolute.startsWith(`${root}${path.sep}`)) {
    throw new HttpError(404, 'file_not_found', 'Fichier introuvable');
  }

  let stat;
  try {
    stat = await fs.promises.stat(absolute);
  } catch {
    throw new HttpError(404, 'file_not_found', 'Fichier introuvable');
  }
  if (!stat.isFile()) throw new HttpError(404, 'file_not_found', 'Fichier introuvable');

  const contentType = MIME_TYPES[path.extname(absolute).toLowerCase()] ?? 'application/octet-stream';
  res.writeHead(200, {
    'Content-Type': contentType,
    'Content-Length': stat.size,
    'Cache-Control': 'public, max-age=31536000, immutable',
    'X-Content-Type-Options': 'nosniff',
  });
  if (req.method === 'HEAD') {
    res.end();
    return;
  }
  const stream = fs.createReadStream(absolute);
  stream.once('error', () => res.destroy());
  stream.pipe(res);
}

async function route(req, res, store) {
  const url = new URL(req.url ?? '/', 'http://localhost');
  const pathname = url.pathname;
  const isRead = req.method === 'GET' || req.method === 'HEAD';

  if (pathname === '/api/health') {
    if (!isRead) return methodNotAllowed(res, ['GET', 'HEAD']);
    return sendJson(res, 200, { status: 'ok', apps: store.apps.length });
  }

  if (pathname === '/api/apps') {
    if (!isRead) return methodNotAllowed(res, ['GET', 'HEAD']);
    return sendJson(res, 200, store.list());
  }

  if (pathname === '/api/publish') {
    if (req.method !== 'POST') return methodNotAllowed(res, ['POST']);
    if (!store.verifyToken(req.headers.authorization)) {
      throw new HttpError(401, 'unauthorized', 'Jeton de publication manquant ou invalide');
    }
    const tempDir = await fs.promises.mkdtemp(path.join(store.dataDir, 'tmp', 'upload-'));
    try {
      const { fields, files } = await parseMultipart(req, { uploadDir: tempDir });
      if (fields.meta === undefined) {
        throw new HttpError(400, 'missing_meta', 'Champ multipart « meta » manquant');
      }
      if (typeof fields.meta !== 'string') {
        throw new HttpError(400, 'invalid_meta', 'Le champ meta doit être un unique champ JSON');
      }
      let meta;
      try {
        meta = JSON.parse(fields.meta);
      } catch {
        throw new HttpError(400, 'invalid_meta', 'Le champ meta n’est pas un JSON valide');
      }
      const result = await store.publish(meta, files);
      const detail = store.publicDetail(result.app);
      const version = detail.versions.find((item) => item.version === result.version.version);
      console.log(
        `[store] publication ${result.created ? 'créée' : 'mise à jour'} ${result.app.slug} ${result.version.version} (${result.version.sizeBytes} octets)`,
      );
      return sendJson(res, 201, {
        created: result.created,
        app: detail,
        version,
        url: `/api/apps/${encodeURIComponent(result.app.slug)}`,
      });
    } finally {
      await fs.promises.rm(tempDir, { recursive: true, force: true }).catch(() => {});
    }
  }

  const downloadMatch = /^\/api\/apps\/([^/]+)\/versions\/([^/]+)\/download$/.exec(pathname);
  if (downloadMatch) {
    if (!isRead) return methodNotAllowed(res, ['GET', 'HEAD']);
    const slug = decodeSegment(downloadMatch[1]);
    const version = decodeSegment(downloadMatch[2]);
    const { record, absolutePath } = store.getDownload(slug, version);
    const stat = await fs.promises.stat(absolutePath);
    if (req.method === 'GET') await store.recordDownload(slug, version);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': stat.size,
      'Content-Disposition': `attachment; filename="${slug}-${record.version}.apk"`,
      'Cache-Control': 'public, max-age=31536000, immutable',
      'Accept-Ranges': 'none',
      'X-Content-Type-Options': 'nosniff',
    });
    if (req.method === 'HEAD') {
      res.end();
      return;
    }
    const stream = fs.createReadStream(absolutePath);
    stream.once('error', () => res.destroy());
    stream.pipe(res);
    return;
  }

  const appMatch = /^\/api\/apps\/([^/]+)$/.exec(pathname);
  if (appMatch) {
    if (!isRead) return methodNotAllowed(res, ['GET', 'HEAD']);
    const slug = decodeSegment(appMatch[1]);
    return sendJson(res, 200, store.detail(slug));
  }

  if (pathname === '/' || pathname === '') {
    if (!isRead) return methodNotAllowed(res, ['GET', 'HEAD']);
    return sendJson(res, 200, {
      name: 'AoE Store',
      status: 'ok',
      endpoints: ['/api/health', '/api/apps', '/api/apps/{slug}', '/api/publish'],
    });
  }

  if (pathname.startsWith('/files/')) {
    if (!isRead) return methodNotAllowed(res, ['GET', 'HEAD']);
    return serveStatic(req, res, store, pathname);
  }

  if (pathname.startsWith('/api/')) {
    throw new HttpError(404, 'not_found', 'Endpoint inconnu');
  }
  throw new HttpError(404, 'not_found', 'Ressource inconnue');
}

export async function createStoreServer(options = {}) {
  const dataDir = path.resolve(
    options.dataDir ?? process.env.AOE_STORE_DATA_DIR ?? DEFAULT_DATA_DIR,
  );
  const store = await new CatalogStore({ dataDir, assetsDir: ASSETS_DIR }).init();
  const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, POST, OPTIONS');
    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }
    const startedAt = Date.now();
    route(req, res, store)
      .catch((error) => {
        if (!(error instanceof HttpError)) {
          console.error('[store] erreur interne', error);
        }
        if (!res.headersSent) sendError(res, error);
        else res.destroy();
      })
      .finally(() => {
        const elapsed = Date.now() - startedAt;
        if (req.url?.startsWith('/api/')) {
          console.log(`[store] ${req.method} ${req.url} ${res.statusCode} ${elapsed}ms`);
        }
      });
  });

  return { server, store, dataDir };
}

export async function startServer() {
  const port = Number(process.env.PORT ?? 8099);
  const host = process.env.HOST ?? '0.0.0.0';
  const { server, store, dataDir } = await createStoreServer();

  server.listen(port, host, () => {
    const address = server.address();
    const interfaces = os.networkInterfaces();
    const lanAddresses = Object.values(interfaces)
      .flat()
      .filter((item) => item && item.family === 'IPv4' && !item.internal)
      .map((item) => `http://${item.address}:${address.port}`);
    console.log(`[store] AoE Store démarré sur http://${host}:${address.port}`);
    for (const lan of lanAddresses) console.log(`[store] accessible sur le LAN : ${lan}`);
    console.log(`[store] données : ${dataDir}`);
    console.log(`[store] jeton de publication : ${store.tokenPath} (jamais exposé par l'API)`);
  });

  const shutdown = () => {
    console.log('[store] arrêt demandé');
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 5000).unref();
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);

  return { server, store, dataDir };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  startServer().catch((error) => {
    console.error('[store] démarrage impossible', error);
    process.exit(1);
  });
}
