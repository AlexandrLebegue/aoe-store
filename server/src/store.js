import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { HttpError } from './errors.js';
import { extractLargestIcon, inspectApk } from '../../shared/apk.js';
import { latestVersionRecord, sortVersionRecords } from '../../shared/semver.js';
import { defaultAccent } from '../../shared/accent.js';

const SLUG_RE = /^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/;
const PACKAGE_RE = /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/;
const ACCENT_RE = /^#[0-9A-Fa-f]{6}$/;

function requireString(meta, key, { max = 200, fallback = undefined, allowEmpty = false } = {}) {
  const raw = meta[key];
  if (raw === undefined || raw === null || String(raw).trim() === '') {
    if (fallback !== undefined) return fallback;
    if (allowEmpty) return '';
    throw new HttpError(400, 'validation_error', `Champ requis manquant : ${key}`);
  }
  const value = String(raw).trim();
  if (value.length > max) {
    throw new HttpError(400, 'validation_error', `Champ trop long : ${key} (maximum ${max} caractères)`);
  }
  return value;
}

function optionalString(meta, key, { max = 200, fallback = '' } = {}) {
  const raw = meta[key];
  if (raw === undefined || raw === null) return fallback;
  const value = String(raw).trim();
  if (value.length > max) {
    throw new HttpError(400, 'validation_error', `Champ trop long : ${key} (maximum ${max} caractères)`);
  }
  return value;
}

function positiveInteger(value, field) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) {
    throw new HttpError(400, 'validation_error', `Champ invalide : ${field} doit être un entier positif`);
  }
  return number;
}

function imageExtension(filePath) {
  const fd = fs.openSync(filePath, 'r');
  const header = Buffer.alloc(12);
  try {
    fs.readSync(fd, header, 0, 12, 0);
  } finally {
    fs.closeSync(fd);
  }
  if (header.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
    return 'png';
  }
  if (header[0] === 0xff && header[1] === 0xd8 && header[2] === 0xff) return 'jpg';
  if (header.toString('ascii', 0, 4) === 'RIFF' && header.toString('ascii', 8, 12) === 'WEBP') return 'webp';
  return null;
}

async function hashFile(filePath) {
  const hash = crypto.createHash('sha256');
  await new Promise((resolve, reject) => {
    const stream = fs.createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.once('error', reject);
    stream.once('end', resolve);
  });
  return hash.digest('hex');
}

async function moveFile(source, destination) {
  await fs.promises.mkdir(path.dirname(destination), { recursive: true });
  try {
    await fs.promises.rename(source, destination);
  } catch (error) {
    if (error.code !== 'EXDEV') throw error;
    await fs.promises.copyFile(source, destination);
    await fs.promises.rm(source, { force: true });
  }
}

export class CatalogStore {
  constructor({ dataDir, assetsDir }) {
    this.dataDir = dataDir;
    this.assetsDir = assetsDir;
    this.blobsDir = path.join(dataDir, 'blobs');
    this.catalogPath = path.join(dataDir, 'catalog.json');
    this.tokenPath = path.join(dataDir, 'publish.token');
    this.catalog = null;
    this.token = null;
    this.queue = Promise.resolve();
  }

  async init() {
    await fs.promises.mkdir(this.blobsDir, { recursive: true });
    await fs.promises.mkdir(path.join(this.dataDir, 'tmp'), { recursive: true });
    await this.#loadCatalog();
    await this.#ensureToken();
    await this.#ensurePlaceholder();
    return this;
  }

  async #loadCatalog() {
    try {
      const raw = await fs.promises.readFile(this.catalogPath, 'utf8');
      const parsed = JSON.parse(raw);
      if (!parsed || !Array.isArray(parsed.apps)) throw new Error('Structure de catalogue invalide');
      this.catalog = { schemaVersion: 1, apps: parsed.apps };
    } catch (error) {
      if (error.code !== 'ENOENT') {
        const backup = `${this.catalogPath}.corrupt-${Date.now()}`;
        await fs.promises.rename(this.catalogPath, backup).catch(() => {});
        console.warn(`[store] catalogue illisible, sauvegarde dans ${backup}`);
      }
      this.catalog = { schemaVersion: 1, apps: [] };
      await this.save();
    }
  }

  async #ensureToken() {
    try {
      const existing = (await fs.promises.readFile(this.tokenPath, 'utf8')).trim();
      if (existing.length >= 32) {
        await fs.promises.chmod(this.tokenPath, 0o600);
        this.token = existing;
        return;
      }
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    const token = crypto.randomBytes(32).toString('hex');
    await fs.promises.mkdir(path.dirname(this.tokenPath), { recursive: true });
    await fs.promises.writeFile(this.tokenPath, `${token}\n`, { mode: 0o600 });
    await fs.promises.chmod(this.tokenPath, 0o600);
    this.token = token;
  }

  async #ensurePlaceholder() {
    const destination = path.join(this.blobsDir, 'assets', 'placeholder-icon.png');
    if (fs.existsSync(destination)) return;
    const source = path.join(this.assetsDir, 'placeholder-icon.png');
    await fs.promises.mkdir(path.dirname(destination), { recursive: true });
    if (fs.existsSync(source)) {
      await fs.promises.copyFile(source, destination);
    }
  }

  verifyToken(authorization) {
    if (typeof authorization !== 'string' || !authorization.startsWith('Bearer ')) return false;
    const provided = Buffer.from(authorization.slice(7).trim());
    const expected = Buffer.from(this.token ?? '');
    if (expected.length === 0 || provided.length !== expected.length) return false;
    return crypto.timingSafeEqual(provided, expected);
  }

  withLock(operation) {
    const run = this.queue.then(operation, operation);
    this.queue = run.catch(() => {});
    return run;
  }

  async save() {
    const temporary = `${this.catalogPath}.tmp`;
    await fs.promises.writeFile(temporary, `${JSON.stringify(this.catalog, null, 2)}\n`, 'utf8');
    await fs.promises.rename(temporary, this.catalogPath);
  }

  get apps() {
    return this.catalog.apps;
  }

  findApp(slug) {
    return this.catalog.apps.find((app) => app.slug === slug) ?? null;
  }

  publicSummary(app) {
    const latest = latestVersionRecord(app.versions);
    return {
      slug: app.slug,
      name: app.name,
      tagline: app.tagline,
      developer: app.developer,
      category: app.category,
      iconUrl: app.iconUrl,
      coverUrls: [...app.coverUrls],
      accentColor: app.accentColor,
      updatedAt: app.updatedAt,
      downloads: app.downloads,
      latestVersion: latest
        ? {
            version: latest.version,
            versionCode: latest.versionCode,
            sizeBytes: latest.sizeBytes,
            publishedAt: latest.publishedAt,
          }
        : null,
    };
  }

  publicVersion(app, record) {
    return {
      version: record.version,
      versionCode: record.versionCode,
      changelog: record.changelog,
      sizeBytes: record.sizeBytes,
      sha256: record.sha256,
      publishedAt: record.publishedAt,
      downloadUrl: `/api/apps/${encodeURIComponent(app.slug)}/versions/${encodeURIComponent(record.version)}/download`,
    };
  }

  publicDetail(app) {
    return {
      ...this.publicSummary(app),
      description: app.description,
      package: app.package,
      createdAt: app.createdAt,
      versions: sortVersionRecords(app.versions).map((record) => this.publicVersion(app, record)),
    };
  }

  list() {
    return this.catalog.apps
      .map((app) => this.publicSummary(app))
      .sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)));
  }

  detail(slug) {
    const app = this.findApp(slug);
    if (!app) throw new HttpError(404, 'app_not_found', `Application introuvable : ${slug}`);
    return this.publicDetail(app);
  }

  async publish(meta, files) {
    return this.withLock(async () => {
      if (!meta || typeof meta !== 'object' || Array.isArray(meta)) {
        throw new HttpError(400, 'invalid_meta', 'Le champ meta doit être un objet JSON');
      }

      const slug = requireString(meta, 'slug', { max: 64 });
      if (!SLUG_RE.test(slug)) {
        throw new HttpError(
          400,
          'invalid_slug',
          'Slug invalide : lettres minuscules, chiffres et tirets uniquement (2 à 64 caractères)',
        );
      }

      const apkFiles = files.filter((file) => file.field === 'apk');
      if (apkFiles.length === 0) {
        throw new HttpError(400, 'missing_apk', 'Fichier APK manquant (champ multipart « apk »)');
      }
      if (apkFiles.length > 1) {
        throw new HttpError(400, 'multiple_apk', 'Un seul fichier APK est autorisé par publication');
      }
      const apkFile = apkFiles[0];

      let apkInfo;
      try {
        apkInfo = inspectApk(apkFile.path);
      } catch (error) {
        throw new HttpError(422, 'invalid_apk', `APK illisible : ${error.message}`);
      }
      if (!apkInfo.packageName || !PACKAGE_RE.test(apkInfo.packageName)) {
        throw new HttpError(422, 'invalid_apk', 'Nom de package Android invalide dans l’APK');
      }

      const declaredPackage = optionalString(meta, 'package', { max: 255, fallback: '' });
      if (declaredPackage && declaredPackage !== apkInfo.packageName) {
        throw new HttpError(
          422,
          'package_mismatch',
          `Le package déclaré (${declaredPackage}) ne correspond pas à celui de l’APK (${apkInfo.packageName})`,
        );
      }

      const version = requireString(meta, 'version', {
        max: 64,
        fallback: apkInfo.versionName ?? undefined,
      });
      const versionCode = positiveInteger(
        meta.versionCode ?? apkInfo.versionCode ?? undefined,
        'versionCode',
      );

      const name = requireString(meta, 'name', { max: 120 });
      const tagline = requireString(meta, 'tagline', { max: 200 });
      const developer = requireString(meta, 'developer', { max: 120 });
      const category = requireString(meta, 'category', { max: 80, fallback: 'Applications' });
      const description = optionalString(meta, 'description', { max: 20000, fallback: tagline });
      const changelog = optionalString(meta, 'changelog', { max: 10000, fallback: '' });
      const accentColorRaw = optionalString(meta, 'accentColor', { max: 7, fallback: '' });
      const accentColor = accentColorRaw ? accentColorRaw.toUpperCase() : defaultAccent(slug);
      if (!ACCENT_RE.test(accentColor)) {
        throw new HttpError(400, 'validation_error', 'accentColor doit être au format #RRGGBB');
      }

      let app = this.findApp(slug);
      const created = !app;
      if (app && app.package !== apkInfo.packageName) {
        throw new HttpError(
          422,
          'package_mismatch',
          `Le package de l’APK (${apkInfo.packageName}) ne correspond pas au package existant de l’application (${app.package})`,
        );
      }
      if (app) {
        const duplicate = app.versions.find(
          (record) => record.version === version || record.versionCode === versionCode,
        );
        if (duplicate) {
          throw new HttpError(
            409,
            'version_exists',
            `La version ${duplicate.version} (versionCode ${duplicate.versionCode}) est déjà publiée`,
          );
        }
      }

      const now = new Date().toISOString();
      const versionDir = `apps/${slug}/versions/v${versionCode}`;
      const absoluteVersionDir = path.join(this.blobsDir, versionDir);
      await fs.promises.mkdir(absoluteVersionDir, { recursive: true });

      const apkDestination = path.join(absoluteVersionDir, 'app.apk');
      await moveFile(apkFile.path, apkDestination);
      const [sha256, stat] = await Promise.all([hashFile(apkDestination), fs.promises.stat(apkDestination)]);

      const iconFiles = files.filter((file) => file.field === 'icon');
      let iconUrl = app?.iconUrl ?? '/files/assets/placeholder-icon.png';
      if (iconFiles.length > 0) {
        const extension = imageExtension(iconFiles[0].path);
        if (!extension) {
          throw new HttpError(400, 'invalid_image', 'L’icône doit être une image PNG, JPEG ou WebP');
        }
        const iconDestination = path.join(absoluteVersionDir, `icon.${extension}`);
        await moveFile(iconFiles[0].path, iconDestination);
        iconUrl = `/files/${versionDir}/icon.${extension}`;
      } else if (!app) {
        try {
          const extracted = extractLargestIcon(apkDestination, path.join(absoluteVersionDir, 'icon.png'));
          if (extracted) iconUrl = `/files/${versionDir}/icon.png`;
        } catch {
          // icône de secours conservée
        }
      }

      const screenshotFiles = files.filter(
        (file) => file.field === 'screenshot' || file.field === 'screenshots',
      );
      let coverUrls = app ? [...app.coverUrls] : [];
      if (screenshotFiles.length > 0) {
        coverUrls = [];
        for (let index = 0; index < screenshotFiles.length; index += 1) {
          const file = screenshotFiles[index];
          const extension = imageExtension(file.path);
          if (!extension) {
            throw new HttpError(
              400,
              'invalid_image',
              `La capture n°${index + 1} doit être une image PNG, JPEG ou WebP`,
            );
          }
          const coverName = `cover-${index + 1}.${extension}`;
          await moveFile(file.path, path.join(absoluteVersionDir, coverName));
          coverUrls.push(`/files/${versionDir}/${coverName}`);
        }
      }

      const record = {
        version,
        versionCode,
        changelog,
        sizeBytes: stat.size,
        sha256,
        publishedAt: now,
        apkFile: `${versionDir}/app.apk`,
      };

      if (!app) {
        app = {
          slug,
          name,
          tagline,
          description,
          developer,
          category,
          package: apkInfo.packageName,
          accentColor,
          iconUrl,
          coverUrls,
          createdAt: now,
          updatedAt: now,
          downloads: 0,
          versions: [],
        };
        this.catalog.apps.push(app);
      } else {
        Object.assign(app, {
          name,
          tagline,
          description,
          developer,
          category,
          accentColor,
          iconUrl,
          coverUrls,
          updatedAt: now,
        });
      }
      app.versions.push(record);
      await this.save();

      return {
        created,
        app,
        version: record,
      };
    });
  }

  getDownload(slug, version) {
    const app = this.findApp(slug);
    if (!app) throw new HttpError(404, 'app_not_found', `Application introuvable : ${slug}`);
    const record = app.versions.find((item) => item.version === version);
    if (!record) {
      throw new HttpError(
        404,
        'version_not_found',
        `Version introuvable pour ${slug} : ${version}`,
      );
    }
    const absolutePath = path.join(this.blobsDir, record.apkFile);
    if (!fs.existsSync(absolutePath)) {
      throw new HttpError(404, 'file_not_found', 'Fichier APK absent du stockage');
    }
    return { app, record, absolutePath };
  }

  async recordDownload(slug, version) {
    return this.withLock(async () => {
      const app = this.findApp(slug);
      const record = app?.versions.find((item) => item.version === version);
      if (!app || !record) return;
      app.downloads += 1;
      await this.save();
    });
  }

  static get slugPattern() {
    return SLUG_RE;
  }
}
