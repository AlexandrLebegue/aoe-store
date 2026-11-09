import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import { openAsBlob } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createStoreServer } from '../src/index.js';

const APK = '/tmp/AoE-Companion-0.9.2-debug.apk';
const ICON = '/home/dev/projects/agent-of-empires/android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png';

function sha256File(filePath) {
  return crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex');
}

async function buildForm(meta, { screenshots = 2 } = {}) {
  const form = new FormData();
  form.append('meta', JSON.stringify(meta));
  form.append('apk', await openAsBlob(APK, { type: 'application/vnd.android.package-archive' }), 'aoe.apk');
  form.append('icon', await openAsBlob(ICON, { type: 'image/png' }), 'icon.png');
  for (let index = 0; index < screenshots; index += 1) {
    form.append('screenshot', await openAsBlob(ICON, { type: 'image/png' }), `cover-${index + 1}.png`);
  }
  return form;
}

const baseMeta = {
  slug: 'aoe-companion',
  name: 'AoE Companion',
  tagline: 'Piloter Agent of Empires depuis le téléphone',
  description: 'Description longue de test.',
  category: 'Outils',
  developer: 'ProxBoss',
  accentColor: '#4F7CFF',
  version: '0.9.2',
  versionCode: 11,
  changelog: 'Première version publiée.',
};

test(
  'API complète : santé, publication, catalogue, téléchargement et erreurs',
  { skip: !fs.existsSync(APK) || !fs.existsSync(ICON), timeout: 180000 },
  async (t) => {
    const dataDir = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'aoe-store-api-'));
    const { server, store } = await createStoreServer({ dataDir });
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    const port = server.address().port;
    const base = `http://127.0.0.1:${port}`;
    t.after(async () => {
      await new Promise((resolve) => server.close(resolve));
      await fs.promises.rm(dataDir, { recursive: true, force: true });
    });

    const health = await fetch(`${base}/api/health`);
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), { status: 'ok', apps: 0 });

    const emptyCatalog = await fetch(`${base}/api/apps`);
    assert.equal(emptyCatalog.status, 200);
    assert.deepEqual(await emptyCatalog.json(), []);

    const unauthorized = await fetch(`${base}/api/publish`, {
      method: 'POST',
      body: await buildForm(baseMeta, { screenshots: 0 }),
    });
    assert.equal(unauthorized.status, 401);
    assert.equal((await unauthorized.json()).error.code, 'unauthorized');

    const mismatch = await fetch(`${base}/api/publish`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${store.token}` },
      body: await buildForm({ ...baseMeta, package: 'dev.proxboss.autre' }, { screenshots: 0 }),
    });
    assert.equal(mismatch.status, 422);
    assert.equal((await mismatch.json()).error.code, 'package_mismatch');

    const publish = await fetch(`${base}/api/publish`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${store.token}` },
      body: await buildForm(baseMeta),
    });
    assert.equal(publish.status, 201);
    const published = await publish.json();
    assert.equal(published.created, true);
    assert.equal(published.app.slug, 'aoe-companion');
    assert.equal(published.app.package, 'dev.proxboss.aoe');
    assert.equal(published.app.coverUrls.length, 2);
    assert.equal(published.version.sha256, sha256File(APK));
    assert.equal(published.version.sizeBytes, fs.statSync(APK).size);

    const list = await fetch(`${base}/api/apps`);
    assert.equal(list.status, 200);
    const apps = await list.json();
    assert.equal(Array.isArray(apps), true);
    assert.equal(apps.length, 1);
    assert.equal(apps[0].latestVersion.version, '0.9.2');
    assert.equal(apps[0].latestVersion.versionCode, 11);
    assert.ok(apps[0].iconUrl.startsWith('/files/apps/aoe-companion/versions/v11/'));

    const detailResponse = await fetch(`${base}/api/apps/aoe-companion`);
    assert.equal(detailResponse.status, 200);
    const detail = await detailResponse.json();
    assert.equal(detail.versions.length, 1);
    assert.equal(detail.versions[0].downloadUrl, '/api/apps/aoe-companion/versions/0.9.2/download');

    const download = await fetch(`${base}${detail.versions[0].downloadUrl}`);
    assert.equal(download.status, 200);
    assert.equal(download.headers.get('content-type'), 'application/vnd.android.package-archive');
    assert.equal(Number(download.headers.get('content-length')), fs.statSync(APK).size);
    const downloaded = Buffer.from(await download.arrayBuffer());
    assert.equal(crypto.createHash('sha256').update(downloaded).digest('hex'), sha256File(APK));

    const head = await fetch(`${base}${detail.versions[0].downloadUrl}`, { method: 'HEAD' });
    assert.equal(head.status, 200);
    assert.equal(Number(head.headers.get('content-length')), fs.statSync(APK).size);

    const icon = await fetch(`${base}${apps[0].iconUrl}`);
    assert.equal(icon.status, 200);
    assert.equal(icon.headers.get('content-type'), 'image/png');
    assert.match(icon.headers.get('cache-control'), /immutable/);
    assert.ok((await icon.arrayBuffer()).byteLength > 1000);

    const duplicate = await fetch(`${base}/api/publish`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${store.token}` },
      body: await buildForm(baseMeta),
    });
    assert.equal(duplicate.status, 409);
    assert.equal((await duplicate.json()).error.code, 'version_exists');

    const update = await fetch(`${base}/api/publish`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${store.token}` },
      body: await buildForm(
        {
          ...baseMeta,
          version: '0.9.3',
          versionCode: 12,
          changelog: 'Deuxième version de test.',
        },
        { screenshots: 0 },
      ),
    });
    assert.equal(update.status, 201);
    const updated = await update.json();
    assert.equal(updated.created, false);
    assert.equal(updated.app.versions.length, 2);
    assert.equal(updated.app.latestVersion.version, '0.9.3');
    assert.equal(updated.app.versions[0].version, '0.9.3');
    assert.equal(updated.app.versions[1].version, '0.9.2');

    const notFound = await fetch(`${base}/api/apps/inconnue`);
    assert.equal(notFound.status, 404);
    assert.equal((await notFound.json()).error.code, 'app_not_found');

    const badMethod = await fetch(`${base}/api/apps`, { method: 'POST' });
    assert.equal(badMethod.status, 405);
    assert.equal((await badMethod.json()).error.code, 'method_not_allowed');

    const traversal = await fetch(`${base}/files/..%2f..%2fdata%2fpublish.token`);
    assert.equal(traversal.status, 404);

    const tokenMode = fs.statSync(path.join(dataDir, 'publish.token')).mode & 0o777;
    assert.equal(tokenMode, 0o600);
  },
);
