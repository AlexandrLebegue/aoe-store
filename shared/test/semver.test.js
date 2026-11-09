import test from 'node:test';
import assert from 'node:assert/strict';
import {
  compareVersions,
  compareVersionRecords,
  latestVersionRecord,
  parseSemver,
  sortVersionRecords,
} from '../semver.js';

test('parseSemver accepte les formats courants', () => {
  assert.deepEqual(parseSemver('1.2.3'), {
    major: 1,
    minor: 2,
    patch: 3,
    prerelease: '',
  });
  assert.equal(parseSemver('v0.9.2').major, 0);
  assert.equal(parseSemver('2.0.0-beta.1').prerelease, 'beta.1');
  assert.equal(parseSemver('n’importe quoi'), null);
});

test('compareVersions respecte l’ordre sémantique', () => {
  assert.ok(compareVersions('1.10.0', '1.9.9') > 0);
  assert.ok(compareVersions('2.0.0', '2.0.0-rc.1') > 0);
  assert.ok(compareVersions('1.0.0', '1.0.1') < 0);
  assert.equal(compareVersions('1.0.0', 'v1.0.0'), 0);
});

test('les versions sont triées par versionCode décroissant puis semver', () => {
  const records = [
    { version: '1.0.0', versionCode: 1 },
    { version: '1.2.0', versionCode: 3 },
    { version: '1.1.0', versionCode: 2 },
  ];
  assert.deepEqual(
    sortVersionRecords(records).map((record) => record.version),
    ['1.2.0', '1.1.0', '1.0.0'],
  );
  assert.equal(latestVersionRecord(records).version, '1.2.0');
  assert.ok(compareVersionRecords({ versionCode: 5 }, { versionCode: 4 }) < 0);
});
