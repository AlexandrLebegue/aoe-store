import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const EOCD_SIG = 0x06054b50;
const CENTRAL_SIG = 0x02014b50;
const LOCAL_SIG = 0x04034b50;
const PNG_SIG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

function findExecutable(name) {
  const pathDirs = (process.env.PATH || '').split(path.delimiter).filter(Boolean);
  for (const dir of pathDirs) {
    const candidate = path.join(dir, name);
    try {
      fs.accessSync(candidate, fs.constants.X_OK);
      return candidate;
    } catch {
      // continue
    }
  }
  return null;
}

function listBuildTools() {
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || '/home/dev/android-sdk';
  const dir = path.join(sdk, 'build-tools');
  try {
    return fs
      .readdirSync(dir)
      .sort((a, b) => b.localeCompare(a, 'en', { numeric: true }))
      .map((version) => path.join(dir, version));
  } catch {
    return [];
  }
}

export function findAapt() {
  if (process.env.AAPT2 && fs.existsSync(process.env.AAPT2)) {
    return { bin: process.env.AAPT2, kind: 'aapt2' };
  }
  if (process.env.AAPT && fs.existsSync(process.env.AAPT)) {
    return { bin: process.env.AAPT, kind: 'aapt' };
  }
  for (const dir of listBuildTools()) {
    const aapt2 = path.join(dir, 'aapt2');
    if (fs.existsSync(aapt2)) return { bin: aapt2, kind: 'aapt2' };
    const aapt = path.join(dir, 'aapt');
    if (fs.existsSync(aapt)) return { bin: aapt, kind: 'aapt' };
  }
  const whichAapt2 = findExecutable('aapt2');
  if (whichAapt2) return { bin: whichAapt2, kind: 'aapt2' };
  const whichAapt = findExecutable('aapt');
  if (whichAapt) return { bin: whichAapt, kind: 'aapt' };
  return null;
}

export function inspectApkWithAapt(apkPath) {
  const aapt = findAapt();
  if (!aapt) return null;
  try {
    const output = execFileSync(aapt.bin, ['dump', 'badging', apkPath], {
      encoding: 'utf8',
      timeout: 30000,
      maxBuffer: 16 * 1024 * 1024,
      stdio: ['ignore', 'pipe', 'ignore'],
    });
    const firstLine = output.split(/\r?\n/, 1)[0] ?? '';
    const name = /name='([^']*)'/.exec(firstLine)?.[1];
    const versionCode = /versionCode='([^']*)'/.exec(firstLine)?.[1];
    const versionName = /versionName='([^']*)'/.exec(firstLine)?.[1];
    if (!name) return null;
    return {
      packageName: name,
      versionCode: versionCode ? Number(versionCode) : null,
      versionName: versionName && versionName !== 'null' ? versionName : null,
      source: aapt.kind,
    };
  } catch {
    return null;
  }
}

export function readEocd(buffer) {
  const minimum = Math.max(0, buffer.length - 65557);
  for (let i = buffer.length - 22; i >= minimum; i -= 1) {
    if (buffer.readUInt32LE(i) === EOCD_SIG) return i;
  }
  throw new Error('Archive ZIP invalide : fin de répertoire central introuvable');
}

export function listZipEntries(filePath) {
  const fd = fs.openSync(filePath, 'r');
  try {
    const stat = fs.fstatSync(fd);
    const tailSize = Math.min(stat.size, 65557);
    const tail = Buffer.alloc(tailSize);
    fs.readSync(fd, tail, 0, tailSize, stat.size - tailSize);
    const eocd = readEocd(tail);
    const count = tail.readUInt16LE(eocd + 10);
    const centralSize = tail.readUInt32LE(eocd + 12);
    const centralOffset = tail.readUInt32LE(eocd + 16);
    if (centralSize === 0xffffffff || centralOffset === 0xffffffff) {
      throw new Error('Archive ZIP64 non prise en charge');
    }
    const central = Buffer.alloc(centralSize);
    fs.readSync(fd, central, 0, centralSize, centralOffset);
    const entries = [];
    let cursor = 0;
    for (let i = 0; i < count; i += 1) {
      if (cursor + 46 > central.length || central.readUInt32LE(cursor) !== CENTRAL_SIG) {
        throw new Error('Répertoire central ZIP corrompu');
      }
      const method = central.readUInt16LE(cursor + 10);
      const compressedSize = central.readUInt32LE(cursor + 20);
      const uncompressedSize = central.readUInt32LE(cursor + 24);
      const nameLength = central.readUInt16LE(cursor + 28);
      const extraLength = central.readUInt16LE(cursor + 30);
      const commentLength = central.readUInt16LE(cursor + 32);
      const localOffset = central.readUInt32LE(cursor + 42);
      const name = central.toString('utf8', cursor + 46, cursor + 46 + nameLength);
      entries.push({ name, method, compressedSize, uncompressedSize, localOffset });
      cursor += 46 + nameLength + extraLength + commentLength;
    }
    return entries;
  } finally {
    fs.closeSync(fd);
  }
}

export function readZipEntryBuffer(filePath, entry) {
  const fd = fs.openSync(filePath, 'r');
  try {
    const header = Buffer.alloc(30);
    fs.readSync(fd, header, 0, 30, entry.localOffset);
    if (header.readUInt32LE(0) !== LOCAL_SIG) throw new Error('En-tête local ZIP invalide');
    const nameLength = header.readUInt16LE(26);
    const extraLength = header.readUInt16LE(28);
    const dataOffset = entry.localOffset + 30 + nameLength + extraLength;
    const raw = Buffer.alloc(entry.compressedSize);
    fs.readSync(fd, raw, 0, entry.compressedSize, dataOffset);
    if (entry.method === 0) return raw;
    if (entry.method === 8) return zlib.inflateRawSync(raw);
    throw new Error(`Compression ZIP non prise en charge (${entry.method})`);
  } finally {
    fs.closeSync(fd);
  }
}

function pngDimensions(buffer) {
  if (buffer.length < 24 || !buffer.subarray(0, 8).equals(PNG_SIG)) return null;
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
}

function iconScore(name, dimensions) {
  const base = path.basename(name).toLowerCase();
  const area = dimensions ? dimensions.width * dimensions.height : 0;
  let tier = 0;
  if (base === 'ic_launcher.png' || base === 'ic_launcher_round.png') tier = 3;
  else if (/^ic_launcher.*\.png$/.test(base)) tier = 2;
  else if (/icon.*\.png$/.test(base)) tier = 1;
  return tier * 10_000_000 + area;
}

export function findLargestIconEntry(filePath) {
  const entries = listZipEntries(filePath);
  const candidates = entries.filter(
    (entry) =>
      entry.name.startsWith('res/') &&
      entry.name.toLowerCase().endsWith('.png') &&
      /(^|\/)(ic_launcher|.*icon)[^/]*\.png$/i.test(entry.name),
  );
  let best = null;
  for (const entry of candidates) {
    try {
      const data = readZipEntryBuffer(filePath, entry);
      const dimensions = pngDimensions(data);
      const score = iconScore(entry.name, dimensions);
      if (!best || score > best.score) best = { entry, dimensions, score };
    } catch {
      // ignore unreadable entries
    }
  }
  return best;
}

export function extractLargestIcon(apkPath, destinationPath) {
  const best = findLargestIconEntry(apkPath);
  if (!best) return null;
  const data = readZipEntryBuffer(apkPath, best.entry);
  fs.mkdirSync(path.dirname(destinationPath), { recursive: true });
  fs.writeFileSync(destinationPath, data);
  return { path: destinationPath, entry: best.entry.name, dimensions: best.dimensions };
}

function readStringPool(buffer, start) {
  const headerSize = buffer.readUInt16LE(start + 2);
  const chunkSize = buffer.readUInt32LE(start + 4);
  const stringCount = buffer.readUInt32LE(start + 8);
  const flags = buffer.readUInt32LE(start + 16);
  const stringsStart = buffer.readUInt32LE(start + 20);
  const isUtf8 = (flags & 0x100) !== 0;
  const strings = [];
  for (let i = 0; i < stringCount; i += 1) {
    const offset = buffer.readUInt32LE(start + headerSize + i * 4);
    const position = start + stringsStart + offset;
    if (position >= start + chunkSize) {
      strings.push('');
      continue;
    }
    if (isUtf8) {
      let cursor = position;
      let length = buffer[cursor];
      cursor += 1;
      if (length & 0x80) {
        length = ((length & 0x7f) << 8) | buffer[cursor];
        cursor += 1;
      }
      let byteLength = buffer[cursor];
      cursor += 1;
      if (byteLength & 0x80) {
        byteLength = ((byteLength & 0x7f) << 8) | buffer[cursor];
        cursor += 1;
      }
      strings.push(buffer.toString('utf8', cursor, cursor + byteLength));
    } else {
      const length = buffer.readUInt16LE(position);
      strings.push(buffer.toString('utf16le', position + 2, position + 2 + length * 2));
    }
  }
  return strings;
}

function typedValue(buffer, cursor, strings) {
  const rawValue = buffer.readInt32LE(cursor + 8);
  const dataType = buffer[cursor + 15];
  const data = buffer.readUInt32LE(cursor + 16);
  if (dataType === 0x03) return strings[data] ?? '';
  if (dataType === 0x10) return data | 0;
  if (dataType === 0x11) return `0x${data.toString(16)}`;
  if (dataType === 0x12) return data !== 0;
  if (rawValue >= 0) return strings[rawValue] ?? '';
  return data;
}

export function parseAxmlManifest(buffer) {
  if (buffer.length < 8 || buffer.readUInt16LE(0) !== 0x0003) {
    throw new Error('AndroidManifest.xml binaire invalide');
  }
  const totalSize = Math.min(buffer.readUInt32LE(4), buffer.length);
  let strings = [];
  let cursor = 8;
  while (cursor + 8 <= totalSize) {
    const chunkType = buffer.readUInt16LE(cursor);
    const chunkSize = buffer.readUInt32LE(cursor + 4);
    if (chunkSize < 8 || cursor + chunkSize > totalSize) break;
    if (chunkType === 0x0001) {
      strings = readStringPool(buffer, cursor);
    } else if (chunkType === 0x0102) {
      const nameIndex = buffer.readUInt32LE(cursor + 20);
      const elementName = strings[nameIndex] ?? '';
      if (elementName === 'manifest') {
        const attributeStart = buffer.readUInt16LE(cursor + 24);
        const attributeSize = buffer.readUInt16LE(cursor + 26);
        const attributeCount = buffer.readUInt16LE(cursor + 28);
        const attributes = {};
        const base = cursor + 16 + attributeStart;
        for (let i = 0; i < attributeCount; i += 1) {
          const attr = base + i * attributeSize;
          if (attr + 20 > cursor + chunkSize) break;
          const attrName = strings[buffer.readUInt32LE(attr + 4)] ?? '';
          attributes[attrName] = typedValue(buffer, attr, strings);
        }
        const versionCode = Number(attributes.versionCode ?? 0);
        const versionName =
          attributes.versionName === undefined || attributes.versionName === ''
            ? null
            : String(attributes.versionName);
        if (!attributes.package) throw new Error('Attribut package absent du manifeste');
        return {
          packageName: String(attributes.package),
          versionCode: Number.isFinite(versionCode) ? versionCode : null,
          versionName,
          source: 'axml',
        };
      }
    }
    cursor += chunkSize;
  }
  throw new Error('Manifeste Android illisible');
}

export function readManifestFromApk(apkPath) {
  const entry = listZipEntries(apkPath).find((item) => item.name === 'AndroidManifest.xml');
  if (!entry) throw new Error('AndroidManifest.xml absent de l’APK');
  return parseAxmlManifest(readZipEntryBuffer(apkPath, entry));
}

export function inspectApk(apkPath) {
  if (!fs.existsSync(apkPath)) {
    throw new Error(`APK introuvable : ${apkPath}`);
  }
  const fd = fs.openSync(apkPath, 'r');
  const magic = Buffer.alloc(4);
  try {
    fs.readSync(fd, magic, 0, 4, 0);
  } finally {
    fs.closeSync(fd);
  }
  if (magic[0] !== 0x50 || magic[1] !== 0x4b) {
    throw new Error('Le fichier fourni n’est pas une archive APK/ZIP valide');
  }
  const fromAapt = inspectApkWithAapt(apkPath);
  if (fromAapt?.packageName) {
    if (fromAapt.versionCode == null || !fromAapt.versionName) {
      try {
        const fallback = readManifestFromApk(apkPath);
        return {
          ...fromAapt,
          versionCode: fromAapt.versionCode ?? fallback.versionCode,
          versionName: fromAapt.versionName ?? fallback.versionName,
        };
      } catch {
        return fromAapt;
      }
    }
    return fromAapt;
  }
  return readManifestFromApk(apkPath);
}
