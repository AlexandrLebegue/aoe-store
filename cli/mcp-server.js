#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '..');
const CLI_PATH = path.join(REPO_ROOT, 'cli', 'bin', 'store-publish.js');
const SERVER_VERSION = '1.0.0';
const DEFAULT_SERVER = 'http://192.168.1.63:8099';
const SUPPORTED_PROTOCOLS = ['2024-11-05', '2025-03-26', '2025-06-18'];

function log(...values) {
  console.error('[aoe-store-mcp]', ...values);
}

function resolveServer(value) {
  let server = String(value ?? process.env.AOE_STORE_URL ?? DEFAULT_SERVER).trim();
  if (!server) server = DEFAULT_SERVER;
  if (!/^https?:\/\//i.test(server)) server = `http://${server}`;
  server = server.replace(/\/+$/, '');
  try {
    const parsed = new URL(server);
    if (!parsed.hostname) throw new Error('hôte manquant');
  } catch {
    throw new Error(`URL de serveur invalide : ${value}`);
  }
  return server;
}

async function fetchJson(url) {
  const response = await fetch(url, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(20000),
  });
  const raw = await response.text();
  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    payload = null;
  }
  if (!response.ok) {
    const message = payload?.error?.message ?? `HTTP ${response.status}`;
    throw new Error(message);
  }
  if (payload === null) throw new Error('Réponse non JSON du serveur');
  return payload;
}

function toolResult(text, structuredContent = undefined, isError = false) {
  const result = { content: [{ type: 'text', text }] };
  if (structuredContent !== undefined) result.structuredContent = structuredContent;
  if (isError) result.isError = true;
  return result;
}

const TOOLS = [
  {
    name: 'store_list_apps',
    description:
      'Liste les applications du catalogue AoE Store avec leur version, taille et nombre de téléchargements.',
    inputSchema: {
      type: 'object',
      properties: {
        server: {
          type: 'string',
          description: 'URL du serveur AoE Store (défaut : AOE_STORE_URL).',
        },
      },
      additionalProperties: false,
    },
  },
  {
    name: 'store_get_app',
    description:
      'Renvoie la fiche complète d’une application : description, captures, historique des versions, SHA-256 et URLs.',
    inputSchema: {
      type: 'object',
      properties: {
        slug: {
          type: 'string',
          description: 'Slug de l’application, par exemple aoe-companion.',
        },
        server: {
          type: 'string',
          description: 'URL du serveur AoE Store (défaut : AOE_STORE_URL).',
        },
      },
      required: ['slug'],
      additionalProperties: false,
    },
  },
  {
    name: 'store_publish',
    description:
      'Publie ou met à jour un APK dans AoE Store. Le jeton est lu depuis AOE_STORE_TOKEN, ' +
      'AOE_STORE_TOKEN_FILE ou data/publish.token ; il ne doit pas être passé en argument.',
    inputSchema: {
      type: 'object',
      properties: {
        apk: {
          type: 'string',
          description: 'Chemin de l’APK à publier (absolu de préférence).',
        },
        slug: {
          type: 'string',
          pattern: '^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$',
          description: 'Identifiant unique de l’application.',
        },
        name: { type: 'string', description: 'Nom affiché.' },
        tagline: { type: 'string', description: 'Accroche courte.' },
        category: { type: 'string', description: 'Catégorie, par exemple Outils.' },
        developer: { type: 'string', description: 'Éditeur.' },
        description: { type: 'string', description: 'Description longue (défaut : accroche).' },
        version: { type: 'string', description: 'versionName (défaut : lu dans l’APK).' },
        versionCode: {
          type: 'integer',
          minimum: 1,
          description: 'versionCode (défaut : lu dans l’APK).',
        },
        changelog: { type: 'string', description: 'Nouveautés de cette version.' },
        icon: { type: 'string', description: 'Icône PNG/JPEG/WebP (défaut : extraite de l’APK).' },
        screenshots: {
          type: 'array',
          items: { type: 'string' },
          description:
            'Captures d’écran. Si absent, trois visuels 1080x1920 sont générés automatiquement.',
        },
        accentColor: {
          type: 'string',
          pattern: '^#[0-9A-Fa-f]{6}$',
          description: 'Couleur d’accent #RRGGBB (défaut : dérivée du slug).',
        },
        package: { type: 'string', description: 'Package Android attendu (vérifié par le serveur).' },
        server: {
          type: 'string',
          description: 'URL du serveur AoE Store (défaut : AOE_STORE_URL).',
        },
        dryRun: {
          type: 'boolean',
          description: 'Valide et prépare la publication sans envoyer.',
        },
        noCoverGeneration: {
          type: 'boolean',
          description: 'Désactive la génération automatique des visuels.',
        },
      },
      required: ['apk', 'slug', 'name', 'tagline', 'category', 'developer'],
      additionalProperties: false,
    },
  },
];

async function listApps(args) {
  const server = resolveServer(args.server);
  const payload = await fetchJson(`${server}/api/apps`);
  const apps = Array.isArray(payload) ? payload : payload?.apps;
  if (!Array.isArray(apps)) throw new Error('Catalogue invalide : tableau attendu');
  const text = `${apps.length} application(s) sur ${server}\n\n${JSON.stringify(apps, null, 2)}`;
  return toolResult(text, { server, apps });
}

async function getApp(args) {
  if (!args.slug || typeof args.slug !== 'string') {
    return toolResult('Le paramètre slug est requis.', undefined, true);
  }
  const server = resolveServer(args.server);
  const app = await fetchJson(`${server}/api/apps/${encodeURIComponent(args.slug)}`);
  return toolResult(JSON.stringify(app, null, 2), { server, app });
}

function publish(args) {
  if (!args.apk || typeof args.apk !== 'string') {
    return toolResult('Le paramètre apk est requis.', undefined, true);
  }
  const server = resolveServer(args.server);
  const argv = [CLI_PATH, '--json', '--server', server];
  const valueOptions = [
    ['slug', '--slug'],
    ['name', '--name'],
    ['tagline', '--tagline'],
    ['description', '--description'],
    ['category', '--category'],
    ['developer', '--developer'],
    ['accentColor', '--accent-color'],
    ['package', '--package'],
    ['version', '--version'],
    ['versionCode', '--version-code'],
    ['changelog', '--changelog'],
    ['icon', '--icon'],
    ['apk', '--apk'],
  ];
  for (const [key, flag] of valueOptions) {
    if (args[key] !== undefined && args[key] !== null && String(args[key]) !== '') {
      argv.push(flag, String(args[key]));
    }
  }
  if (Array.isArray(args.screenshots)) {
    for (const screenshot of args.screenshots) {
      if (typeof screenshot === 'string' && screenshot !== '') {
        argv.push('--screenshot', screenshot);
      }
    }
  }
  if (args.dryRun === true) argv.push('--dry-run');
  if (args.noCoverGeneration === true) argv.push('--no-cover-generation');

  log(`publication ${args.slug ?? '(sans slug)'} ${args.version ?? ''} vers ${server}`);
  const result = spawnSync(process.execPath, argv, {
    encoding: 'utf8',
    timeout: 15 * 60 * 1000,
    maxBuffer: 64 * 1024 * 1024,
    env: process.env,
  });
  const stdout = (result.stdout ?? '').trim();
  let payload = null;
  try {
    payload = JSON.parse(stdout);
  } catch {
    payload = null;
  }
  if (result.error) {
    return toolResult(`Échec du CLI : ${result.error.message}`, undefined, true);
  }
  if (result.status !== 0 || !payload || payload.ok !== true) {
    const stderr = (result.stderr ?? '').trim();
    const message =
      payload?.error?.message ||
      stderr ||
      stdout ||
      `Le CLI a échoué avec le code ${result.status}`;
    return toolResult(`Publication impossible : ${message}`, payload ?? undefined, true);
  }

  const summary = payload.dryRun
    ? `Dry-run OK : ${payload.name} ${payload.version} — ${payload.detailUrl}`
    : [
        `Publication réussie : ${payload.name} ${payload.version} (versionCode ${payload.versionCode})`,
        `Détail : ${payload.detailUrl}`,
        `Téléchargement : ${payload.downloadUrl}`,
        `SHA-256 : ${payload.sha256}`,
      ].join('\n');
  return toolResult(`${summary}\n\n${JSON.stringify(payload, null, 2)}`, payload);
}

async function callTool(name, args) {
  switch (name) {
    case 'store_list_apps':
      return listApps(args);
    case 'store_get_app':
      return getApp(args);
    case 'store_publish':
      return publish(args);
    default:
      return toolResult(`Outil inconnu : ${name}`, undefined, true);
  }
}

function send(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

async function handleMessage(message) {
  if (!message || typeof message !== 'object' || message.jsonrpc !== '2.0') {
    if (message?.id !== undefined) {
      send({
        jsonrpc: '2.0',
        id: message.id,
        error: { code: -32600, message: 'Requête JSON-RPC invalide' },
      });
    }
    return;
  }
  const { id, method, params } = message;
  const isNotification = id === undefined;

  if (method === 'initialize') {
    const requested = params?.protocolVersion;
    const protocolVersion = SUPPORTED_PROTOCOLS.includes(requested)
      ? requested
      : '2025-06-18';
    send({
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion,
        capabilities: { tools: { listChanged: false } },
        serverInfo: { name: 'aoe-store', version: SERVER_VERSION },
        instructions:
          'Serveur MCP de la boutique AoE Store. Utilise store_list_apps et store_get_app ' +
          'pour consulter le catalogue, puis store_publish pour publier un APK.',
      },
    });
    return;
  }

  if (method === 'notifications/initialized' || method === 'notifications/cancelled') {
    return;
  }

  if (method === 'ping') {
    if (!isNotification) send({ jsonrpc: '2.0', id, result: {} });
    return;
  }

  if (method === 'tools/list') {
    if (!isNotification) send({ jsonrpc: '2.0', id, result: { tools: TOOLS } });
    return;
  }

  if (method === 'tools/call') {
    if (isNotification) return;
    const toolName = params?.name;
    const args = params?.arguments ?? {};
    if (!toolName || typeof toolName !== 'string') {
      send({
        jsonrpc: '2.0',
        id,
        result: toolResult('Le nom de l’outil est requis.', undefined, true),
      });
      return;
    }
    try {
      const result = await callTool(toolName, args);
      send({ jsonrpc: '2.0', id, result });
    } catch (error) {
      send({
        jsonrpc: '2.0',
        id,
        result: toolResult(`Erreur : ${error.message}`, undefined, true),
      });
    }
    return;
  }

  if (!isNotification) {
    send({
      jsonrpc: '2.0',
      id,
      error: { code: -32601, message: `Méthode inconnue : ${method}` },
    });
  }
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
let queue = Promise.resolve();

rl.on('line', (line) => {
  const trimmed = line.trim();
  if (!trimmed) return;
  queue = queue
    .then(async () => {
      let message;
      try {
        message = JSON.parse(trimmed);
      } catch {
        send({
          jsonrpc: '2.0',
          id: null,
          error: { code: -32700, message: 'Erreur de parsing JSON' },
        });
        return;
      }
      await handleMessage(message);
    })
    .catch((error) => {
      log('erreur inattendue', error);
    });
});

rl.on('close', () => {
  queue.finally(() => process.exit(0));
});

process.on('SIGINT', () => process.exit(0));
process.on('SIGTERM', () => process.exit(0));
process.stdout.on('error', (error) => {
  if (error.code === 'EPIPE') process.exit(0);
});

log(`prêt (serveur par défaut : ${process.env.AOE_STORE_URL || DEFAULT_SERVER})`);
