import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { extractLargestIcon, inspectApk, listZipEntries } from '../apk.js';

const APK = '/tmp/AoE-Companion-0.9.2-debug.apk';

test('inspecte un APK réel avec aapt2 ou le parseur AXML de secours', { skip: !fs.existsSync(APK) }, () => {
  const info = inspectApk(APK);
  assert.equal(info.packageName, 'dev.proxboss.aoe');
  assert.equal(info.versionCode, 11);
  assert.equal(info.versionName, '0.9.2');
  assert.ok(['aapt2', 'aapt', 'axml'].includes(info.source));
});

test('liste les entrées d’un APK et extrait la plus grande icône', { skip: !fs.existsSync(APK) }, () => {
  const entries = listZipEntries(APK);
  assert.ok(entries.some((entry) => entry.name === 'AndroidManifest.xml'));
  const destination = path.join(os.tmpdir(), `aoe-store-icon-${process.pid}.png`);
  const result = extractLargestIcon(APK, destination);
  assert.ok(result);
  assert.ok(fs.statSync(destination).size > 1000);
  fs.rmSync(destination, { force: true });
});

test('refuse un fichier qui n’est pas un APK', () => {
  const fake = path.join(os.tmpdir(), `aoe-store-fake-${process.pid}.apk`);
  fs.writeFileSync(fake, 'pas un apk');
  assert.throws(() => inspectApk(fake), /APK\/ZIP valide/);
  fs.rmSync(fake, { force: true });
});
