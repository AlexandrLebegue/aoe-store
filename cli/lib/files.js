import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

export function humanSize(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return 'inconnue';
  if (bytes < 1024) return `${bytes} o`;
  const units = ['Ko', 'Mo', 'Go', 'To'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[unit]}`;
}

export async function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  await new Promise((resolve, reject) => {
    const stream = fs.createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.once('error', reject);
    stream.once('end', resolve);
  });
  return hash.digest('hex');
}

export function imageMimeType(filePath) {
  const fd = fs.openSync(filePath, 'r');
  const header = Buffer.alloc(12);
  try {
    fs.readSync(fd, header, 0, 12, 0);
  } finally {
    fs.closeSync(fd);
  }
  if (header.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
    return 'image/png';
  }
  if (header[0] === 0xff && header[1] === 0xd8 && header[2] === 0xff) return 'image/jpeg';
  if (header.toString('ascii', 0, 4) === 'RIFF' && header.toString('ascii', 8, 12) === 'WEBP') {
    return 'image/webp';
  }
  return null;
}

export async function makeTempDir() {
  return fs.promises.mkdtemp(path.join(os.tmpdir(), 'aoe-store-publish-'));
}

export async function readTokenFile(filePath) {
  try {
    const value = (await fs.promises.readFile(filePath, 'utf8')).trim();
    return value || null;
  } catch {
    return null;
  }
}

export async function resolveToken(options) {
  if (options.token) return { token: options.token.trim(), source: 'option --token' };
  if (process.env.AOE_STORE_TOKEN) {
    return { token: process.env.AOE_STORE_TOKEN.trim(), source: 'variable AOE_STORE_TOKEN' };
  }

  const explicitFile = options.tokenFile || process.env.AOE_STORE_TOKEN_FILE;
  if (explicitFile) {
    const token = await readTokenFile(path.resolve(explicitFile));
    if (!token) throw new Error(`Jeton illisible dans ${path.resolve(explicitFile)}`);
    return { token, source: explicitFile };
  }

  const candidates = [
    path.join(REPO_ROOT, 'data', 'publish.token'),
    path.join(os.homedir(), '.config', 'aoe-store', 'publish.token'),
  ];
  for (const candidate of candidates) {
    const token = await readTokenFile(candidate);
    if (token) return { token, source: candidate };
  }
  return { token: null, source: null };
}
