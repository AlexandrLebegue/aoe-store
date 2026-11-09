import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { HttpError } from './errors.js';

function unquote(value) {
  return value.replace(/\\(.)/g, '$1');
}

function parseDisposition(value) {
  const name = /name="((?:[^"\\]|\\.)*)"/i.exec(value)?.[1];
  const filename = /filename="((?:[^"\\]|\\.)*)"/i.exec(value)?.[1];
  return {
    name: name === undefined ? null : unquote(name),
    filename: filename === undefined ? null : unquote(filename),
  };
}

function parsePartHeaders(text) {
  const headers = {};
  for (const line of text.split(/\r\n/)) {
    const index = line.indexOf(':');
    if (index <= 0) continue;
    const key = line.slice(0, index).trim().toLowerCase();
    const value = line.slice(index + 1).trim();
    headers[key] = value;
  }
  return headers;
}

function writeChunk(stream, data) {
  return new Promise((resolve, reject) => {
    stream.write(data, (error) => (error ? reject(error) : resolve()));
  });
}

function endStream(stream) {
  return new Promise((resolve, reject) => {
    stream.end((error) => (error ? reject(error) : resolve()));
  });
}

export async function parseMultipart(req, options = {}) {
  const uploadDir = options.uploadDir;
  const maxFileSize = options.maxFileSize ?? 1024 * 1024 * 1024;
  const maxFieldSize = options.maxFieldSize ?? 2 * 1024 * 1024;
  const maxFiles = options.maxFiles ?? 24;
  const maxBodySize = options.maxBodySize ?? 1536 * 1024 * 1024;

  const contentType = String(req.headers['content-type'] ?? '');
  const boundaryMatch = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(contentType);
  if (!/^multipart\/form-data\b/i.test(contentType) || !boundaryMatch) {
    throw new HttpError(
      400,
      'invalid_multipart',
      'Content-Type multipart/form-data avec un boundary est requis',
    );
  }
  if (!uploadDir) throw new Error('uploadDir est requis');

  const boundary = (boundaryMatch[1] || boundaryMatch[2]).trim();
  if (!boundary || boundary.length > 200) {
    throw new HttpError(400, 'invalid_multipart', 'Boundary multipart invalide');
  }

  await fs.promises.mkdir(uploadDir, { recursive: true });

  const fields = Object.create(null);
  const files = [];
  const createdFiles = [];
  const firstDelimiter = Buffer.from(`--${boundary}`);
  const delimiter = Buffer.from(`\r\n--${boundary}`);

  let buffer = Buffer.alloc(0);
  let state = 'preamble';
  let current = null;
  let finished = false;
  let received = 0;

  const cleanup = async () => {
    for (const file of createdFiles) {
      await fs.promises.rm(file.path, { force: true }).catch(() => {});
    }
  };

  const startPart = (headers) => {
    const disposition = headers['content-disposition'] ?? '';
    const { name, filename } = parseDisposition(disposition);
    if (!name) throw new HttpError(400, 'invalid_multipart', 'Partie multipart sans nom de champ');
    if (filename !== null) {
      if (files.length >= maxFiles) {
        throw new HttpError(413, 'too_many_files', `Trop de fichiers (maximum ${maxFiles})`);
      }
      const tempPath = path.join(uploadDir, `part-${randomUUID()}`);
      const stream = fs.createWriteStream(tempPath);
      current = {
        type: 'file',
        field: name,
        filename: path.basename(filename || 'fichier'),
        mimeType: headers['content-type'] ?? 'application/octet-stream',
        path: tempPath,
        size: 0,
        stream,
      };
      createdFiles.push(current);
    } else {
      current = { type: 'field', field: name, chunks: [], size: 0 };
    }
  };

  const writeCurrent = async (data) => {
    if (!current || data.length === 0) return;
    if (current.type === 'file') {
      current.size += data.length;
      if (current.size > maxFileSize) {
        throw new HttpError(
          413,
          'file_too_large',
          `Fichier trop volumineux (maximum ${Math.round(maxFileSize / (1024 * 1024))} Mo)`,
        );
      }
      await writeChunk(current.stream, data);
    } else {
      current.size += data.length;
      if (current.size > maxFieldSize) {
        throw new HttpError(413, 'field_too_large', 'Champ multipart trop volumineux');
      }
      current.chunks.push(data);
    }
  };

  const endPart = async () => {
    if (!current) return;
    if (current.type === 'file') {
      await endStream(current.stream);
      files.push({
        field: current.field,
        filename: current.filename,
        mimeType: current.mimeType,
        path: current.path,
        size: current.size,
      });
    } else {
      const value = Buffer.concat(current.chunks).toString('utf8');
      if (fields[current.field] === undefined) fields[current.field] = value;
      else if (Array.isArray(fields[current.field])) fields[current.field].push(value);
      else fields[current.field] = [fields[current.field], value];
    }
    current = null;
  };

  const processBuffer = async () => {
    while (!finished) {
      if (state === 'preamble') {
        const index = buffer.indexOf(firstDelimiter);
        if (index === -1) {
          if (buffer.length > 65536) {
            buffer = buffer.subarray(buffer.length - firstDelimiter.length - 4);
          }
          return;
        }
        buffer = buffer.subarray(index + firstDelimiter.length);
        state = 'after-boundary';
        continue;
      }

      if (state === 'after-boundary') {
        if (buffer.length < 2) return;
        if (buffer[0] === 0x2d && buffer[1] === 0x2d) {
          finished = true;
          buffer = Buffer.alloc(0);
          return;
        }
        if (buffer[0] === 0x0d && buffer[1] === 0x0a) {
          buffer = buffer.subarray(2);
          state = 'headers';
          continue;
        }
        throw new HttpError(400, 'invalid_multipart', 'Séparateur multipart invalide');
      }

      if (state === 'headers') {
        const index = buffer.indexOf('\r\n\r\n');
        if (index === -1) {
          if (buffer.length > 65536) {
            throw new HttpError(400, 'invalid_multipart', 'En-têtes multipart trop volumineux');
          }
          return;
        }
        const headerText = buffer.subarray(0, index).toString('utf8');
        buffer = buffer.subarray(index + 4);
        startPart(parsePartHeaders(headerText));
        state = 'body';
        continue;
      }

      if (state === 'body') {
        const index = buffer.indexOf(delimiter);
        if (index === -1) {
          const safeLength = buffer.length - (delimiter.length + 4);
          if (safeLength > 0) {
            await writeCurrent(buffer.subarray(0, safeLength));
            buffer = buffer.subarray(safeLength);
          }
          return;
        }
        await writeCurrent(buffer.subarray(0, index));
        buffer = buffer.subarray(index + delimiter.length);
        await endPart();
        state = 'after-boundary';
        continue;
      }
    }
  };

  try {
    for await (const chunk of req) {
      received += chunk.length;
      if (received > maxBodySize) {
        throw new HttpError(413, 'body_too_large', 'Corps de requête trop volumineux');
      }
      buffer = buffer.length === 0 ? chunk : Buffer.concat([buffer, chunk]);
      await processBuffer();
      if (finished) break;
    }
    if (!finished) await processBuffer();
    if (!finished) {
      throw new HttpError(400, 'invalid_multipart', 'Corps multipart incomplet');
    }
    return { fields, files };
  } catch (error) {
    await cleanup();
    throw error;
  }
}
