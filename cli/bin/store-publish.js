#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import { openAsBlob } from 'node:fs';
import path from 'node:path';
import { inspectApk, extractLargestIcon } from '../../shared/apk.js';
import { defaultAccent } from '../../shared/accent.js';
import { parseArgs, usage, UsageError } from '../lib/cli-args.js';
import {
  REPO_ROOT,
  humanSize,
  imageMimeType,
  makeTempDir,
  resolveToken,
  sha256File,
} from '../lib/files.js';

const SLUG_RE = /^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/;
const ACCENT_RE = /^#[0-9A-Fa-f]{6}$/;

function fail(message) {
  throw new Error(message);
}

function normalizeServer(value) {
  let server = String(value ?? '').trim();
  if (!server) server = 'http://127.0.0.1:8099';
  if (!/^https?:\/\//i.test(server)) server = `http://${server}`;
  server = server.replace(/\/+$/, '');
  try {
    const parsed = new URL(server);
    if (!parsed.hostname) throw new Error('hôte manquant');
  } catch {
    throw new UsageError(`URL de serveur invalide : ${value}`);
  }
  return server;
}

function requireOption(options, key, label) {
  if (!options[key] || String(options[key]).trim() === '') {
    throw new UsageError(`Option obligatoire manquante : ${label}`);
  }
  return String(options[key]).trim();
}

function checkImage(filePath, label) {
  if (!fs.existsSync(filePath)) fail(`${label} introuvable : ${filePath}`);
  const stat = fs.statSync(filePath);
  if (!stat.isFile()) fail(`${label} n’est pas un fichier : ${filePath}`);
  const mimeType = imageMimeType(filePath);
  if (!mimeType) fail(`${label} n’est pas une image PNG, JPEG ou WebP : ${filePath}`);
  return mimeType;
}

function printSummary(summary) {
  console.log('');
  console.log('AoE Store — publication');
  console.log(`  Serveur       : ${summary.server}`);
  console.log(`  Application   : ${summary.name} (${summary.slug})`);
  console.log(`  Version       : ${summary.version} (code ${summary.versionCode})`);
  console.log(`  Package       : ${summary.package}`);
  console.log(`  Éditeur       : ${summary.developer} — ${summary.category}`);
  console.log(`  APK           : ${summary.apkPath} (${humanSize(summary.sizeBytes)})`);
  console.log(`  SHA-256       : ${summary.sha256}`);
  console.log(`  Icône         : ${summary.iconLabel}`);
  console.log(`  Captures      : ${summary.coversLabel}`);
  console.log(`  Détail public : ${summary.detailUrl}`);
  console.log('');
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    process.stdout.write(usage());
    return 0;
  }

  const server = normalizeServer(options.server ?? process.env.AOE_STORE_URL);
  const slug = requireOption(options, 'slug', '--slug');
  if (!SLUG_RE.test(slug)) {
    throw new UsageError(
      'Slug invalide : utilisez des minuscules, chiffres et tirets (2 à 64 caractères)',
    );
  }
  const name = requireOption(options, 'name', '--name');
  const tagline = requireOption(options, 'tagline', '--tagline');
  const developer = requireOption(options, 'developer', '--developer');
  const category = requireOption(options, 'category', '--category');
  const apkOption = requireOption(options, 'apk', '--apk');
  const apkPath = path.resolve(apkOption);

  if (!fs.existsSync(apkPath)) fail(`APK introuvable : ${apkPath}`);
  const apkStat = fs.statSync(apkPath);
  if (!apkStat.isFile()) fail(`APK invalide (pas un fichier) : ${apkPath}`);

  let apkInfo;
  try {
    apkInfo = inspectApk(apkPath);
  } catch (error) {
    fail(`APK illisible : ${error.message}`);
  }
  if (!apkInfo.packageName) fail('Impossible de lire le package Android depuis l’APK');

  if (options.package && options.package !== apkInfo.packageName) {
    fail(
      `Le package déclaré (${options.package}) ne correspond pas à celui de l’APK (${apkInfo.packageName})`,
    );
  }

  const version = options.version ?? apkInfo.versionName;
  if (!version) {
    fail('versionName illisible dans l’APK : précisez --version');
  }
  const versionCode = Number(options.versionCode ?? apkInfo.versionCode);
  if (!Number.isInteger(versionCode) || versionCode <= 0) {
    fail('versionCode illisible dans l’APK : précisez --version-code');
  }

  if (options.version && apkInfo.versionName && options.version !== apkInfo.versionName) {
    console.warn(
      `Attention : --version ${options.version} diffère du versionName de l’APK (${apkInfo.versionName}).`,
    );
  }
  if (
    options.versionCode !== undefined &&
    apkInfo.versionCode &&
    Number(options.versionCode) !== apkInfo.versionCode
  ) {
    console.warn(
      `Attention : --version-code ${options.versionCode} diffère du versionCode de l’APK (${apkInfo.versionCode}).`,
    );
  }

  const accentColor = String(options.accentColor ?? defaultAccent(slug)).toUpperCase();
  if (!ACCENT_RE.test(accentColor)) {
    throw new UsageError('--accent-color doit être au format #RRGGBB');
  }

  const tempDir = await makeTempDir();
  try {
    let iconPath = options.icon ? path.resolve(options.icon) : null;
    let iconLabel;
    if (iconPath) {
      checkImage(iconPath, 'Icône');
      iconLabel = iconPath;
    } else {
      try {
        const extracted = extractLargestIcon(apkPath, path.join(tempDir, 'icon.png'));
        if (extracted) {
          iconPath = extracted.path;
          iconLabel = `extraite de l’APK (${extracted.entry})`;
        } else {
          iconLabel = 'aucune (le serveur utilisera une icône de secours)';
        }
      } catch {
        iconLabel = 'aucune (le serveur utilisera une icône de secours)';
      }
    }

    let screenshots = (options.screenshots ?? []).map((file) => path.resolve(file));
    for (const [index, screenshot] of screenshots.entries()) {
      checkImage(screenshot, `Capture n°${index + 1}`);
    }
    let coversLabel;
    if (screenshots.length === 0 && !options.noCoverGeneration) {
      const python = process.env.AOE_STORE_PYTHON || '/opt/iconvenv/bin/python';
      const script = path.join(REPO_ROOT, 'tools', 'gen-covers.py');
      if (!fs.existsSync(python)) fail(`Python Pillow introuvable : ${python}`);
      if (!fs.existsSync(script)) fail(`Script de génération introuvable : ${script}`);
      const outputDir = path.join(tempDir, 'covers');
      console.log('Aucune capture fournie : génération de 3 visuels 1080x1920…');
      const args = [
        script,
        '--output-dir',
        outputDir,
        '--name',
        name,
        '--tagline',
        tagline,
        '--version',
        String(version),
        '--accent',
        accentColor,
        '--developer',
        developer,
        '--category',
        category,
      ];
      if (iconPath) args.push('--icon', iconPath);
      const result = spawnSync(python, args, { encoding: 'utf8', timeout: 120000 });
      if (result.error) fail(`Génération des visuels impossible : ${result.error.message}`);
      if (result.status !== 0) {
        fail(`Génération des visuels impossible : ${(result.stderr || result.stdout || '').trim()}`);
      }
      let parsed;
      try {
        parsed = JSON.parse(result.stdout);
      } catch {
        fail('Sortie invalide du générateur de visuels');
      }
      screenshots = parsed.covers;
      coversLabel = `${screenshots.length} visuels générés automatiquement`;
    } else if (screenshots.length > 0) {
      coversLabel = `${screenshots.length} capture(s) fournie(s)`;
    } else {
      coversLabel = 'aucune (génération désactivée)';
    }

    const sha256 = await sha256File(apkPath);
    const sizeBytes = apkStat.size;
    const detailUrl = `${server}/api/apps/${encodeURIComponent(slug)}`;
    const meta = {
      slug,
      name,
      tagline,
      description: options.description ?? tagline,
      category,
      developer,
      accentColor,
      package: apkInfo.packageName,
      version: String(version),
      versionCode,
      changelog: options.changelog ?? '',
    };

    printSummary({
      server,
      slug,
      name,
      version,
      versionCode,
      package: apkInfo.packageName,
      developer,
      category,
      apkPath,
      sizeBytes,
      sha256,
      iconLabel,
      coversLabel,
      detailUrl,
    });

    if (options.dryRun) {
      console.log('Dry-run : aucun envoi effectué.');
      console.log(`URL de détail qui serait utilisée : ${detailUrl}`);
      return 0;
    }

    const { token, source } = await resolveToken(options);
    if (!token) {
      fail(
        'Jeton de publication introuvable. Démarrez le serveur puis utilisez --token, ' +
          `AOE_STORE_TOKEN ou le fichier ${path.join(REPO_ROOT, 'data', 'publish.token')}.`,
      );
    }

    console.log(`Envoi de ${humanSize(sizeBytes)} vers ${server} (jeton : ${source})…`);
    const form = new FormData();
    form.append('meta', JSON.stringify(meta));
    form.append(
      'apk',
      await openAsBlob(apkPath, { type: 'application/vnd.android.package-archive' }),
      path.basename(apkPath),
    );
    if (iconPath) {
      form.append('icon', await openAsBlob(iconPath, { type: imageMimeType(iconPath) }), path.basename(iconPath));
    }
    for (const screenshot of screenshots) {
      form.append(
        'screenshot',
        await openAsBlob(screenshot, { type: imageMimeType(screenshot) }),
        path.basename(screenshot),
      );
    }

    let response;
    const startedAt = Date.now();
    try {
      response = await fetch(`${server}/api/publish`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: form,
      });
    } catch (error) {
      const cause = error.cause?.code ? ` (${error.cause.code})` : '';
      fail(`Connexion au serveur impossible : ${server}${cause}. Vérifiez que le serveur est démarré.`);
    }

    const rawBody = await response.text();
    let payload = null;
    try {
      payload = JSON.parse(rawBody);
    } catch {
      // corps non JSON
    }

    if (!response.ok) {
      const code = payload?.error?.code;
      const message = payload?.error?.message ?? rawBody.slice(0, 500) ?? 'réponse vide';
      fail(
        `Publication refusée (HTTP ${response.status}${code ? `, ${code}` : ''}) : ${message}`,
      );
    }

    const elapsed = ((Date.now() - startedAt) / 1000).toFixed(1);
    const downloadUrl = `${server}${payload?.version?.downloadUrl ?? ''}`;
    console.log(`Publication réussie (${payload?.created ? 'création' : 'mise à jour'}) en ${elapsed}s.`);
    console.log(`  Version publiée : ${payload?.version?.version ?? version}`);
    console.log(`  Téléchargement  : ${downloadUrl}`);
    console.log(`  Détail public   : ${detailUrl}`);
    return 0;
  } finally {
    await fs.promises.rm(tempDir, { recursive: true, force: true }).catch(() => {});
  }
}

try {
  const code = await main();
  process.exitCode = code;
} catch (error) {
  if (error instanceof UsageError) {
    console.error(`Erreur : ${error.message}`);
    console.error('Utilisez --help pour afficher l’aide.');
    process.exitCode = 2;
  } else {
    console.error(`Erreur : ${error.message}`);
    process.exitCode = 1;
  }
}
