import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import path from 'node:path';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const MCP_SERVER = path.resolve(HERE, '..', 'mcp-server.js');

function startFakeStore() {
  const server = http.createServer((req, res) => {
    const sendJson = (status, payload) => {
      const body = JSON.stringify(payload);
      res.writeHead(status, {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
      });
      res.end(body);
    };
    if (req.url === '/api/apps') {
      return sendJson(200, [
        {
          slug: 'demo',
          name: 'Demo',
          latestVersion: { version: '1.0.0', versionCode: 1, sizeBytes: 42 },
        },
      ]);
    }
    if (req.url === '/api/apps/demo') {
      return sendJson(200, { slug: 'demo', name: 'Demo', versions: [] });
    }
    return sendJson(404, {
      error: { code: 'not_found', message: 'Application introuvable' },
    });
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      resolve({ server, url: `http://127.0.0.1:${server.address().port}` });
    });
  });
}

function startClient(environment) {
  const child = spawn(process.execPath, [MCP_SERVER], {
    env: { ...process.env, ...environment },
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  const pending = new Map();
  const stderr = [];
  let nextId = 1;
  child.stderr.on('data', (chunk) => stderr.push(chunk.toString()));
  const reader = readline.createInterface({ input: child.stdout });
  reader.on('line', (line) => {
    let message;
    try {
      message = JSON.parse(line);
    } catch {
      return;
    }
    if (message.id === undefined || !pending.has(message.id)) return;
    const entry = pending.get(message.id);
    clearTimeout(entry.timer);
    pending.delete(message.id);
    entry.resolve(message);
  });

  const request = (method, params) =>
    new Promise((resolve, reject) => {
      const id = nextId;
      nextId += 1;
      const timer = setTimeout(() => {
        pending.delete(id);
        reject(new Error(`Timeout MCP ${method}. stderr=${stderr.join('')}`));
      }, 5000);
      pending.set(id, { resolve, timer });
      child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`);
    });

  const notify = (method, params) => {
    child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', method, params })}\n`);
  };

  const close = () => {
    reader.close();
    child.kill();
  };

  return { request, notify, close, stderr };
}

test(
  'MCP : initialisation, liste des outils, lecture et erreurs',
  { timeout: 30000 },
  async (t) => {
    const { server, url } = await startFakeStore();
    const client = startClient({ AOE_STORE_URL: url });
    t.after(async () => {
      client.close();
      await new Promise((resolve) => server.close(resolve));
    });

    const initialize = await client.request('initialize', {
      protocolVersion: '2025-06-18',
      capabilities: {},
      clientInfo: { name: 'test', version: '1.0.0' },
    });
    assert.equal(initialize.result.serverInfo.name, 'aoe-store');
    assert.ok(initialize.result.capabilities.tools);
    client.notify('notifications/initialized');

    const tools = await client.request('tools/list');
    assert.deepEqual(
      tools.result.tools.map((tool) => tool.name).sort(),
      ['store_get_app', 'store_list_apps', 'store_publish'],
    );

    const list = await client.request('tools/call', {
      name: 'store_list_apps',
      arguments: {},
    });
    assert.equal(list.result.isError, undefined);
    assert.match(list.result.content[0].text, /1 application/);
    assert.equal(list.result.structuredContent.apps[0].slug, 'demo');

    const detail = await client.request('tools/call', {
      name: 'store_get_app',
      arguments: { slug: 'demo' },
    });
    assert.equal(detail.result.structuredContent.app.name, 'Demo');

    const missingSlug = await client.request('tools/call', {
      name: 'store_get_app',
      arguments: {},
    });
    assert.equal(missingSlug.result.isError, true);

    const missingApk = await client.request('tools/call', {
      name: 'store_publish',
      arguments: {},
    });
    assert.equal(missingApk.result.isError, true);
    assert.match(missingApk.result.content[0].text, /apk/);

    const unknown = await client.request('tools/call', {
      name: 'outil_inconnu',
      arguments: {},
    });
    assert.equal(unknown.result.isError, true);
  },
);
