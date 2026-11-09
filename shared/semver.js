const SEMVER_RE = /^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?$/;

export function parseSemver(value) {
  const match = SEMVER_RE.exec(String(value ?? '').trim());
  if (!match) return null;
  return {
    major: Number(match[1] ?? 0),
    minor: Number(match[2] ?? 0),
    patch: Number(match[3] ?? 0),
    prerelease: match[4] ?? '',
  };
}

export function compareVersions(a, b) {
  const pa = parseSemver(a);
  const pb = parseSemver(b);
  if (!pa && !pb) return String(a).localeCompare(String(b), 'en', { numeric: true });
  if (!pa) return -1;
  if (!pb) return 1;

  for (const key of ['major', 'minor', 'patch']) {
    if (pa[key] !== pb[key]) return pa[key] - pb[key];
  }
  if (pa.prerelease === pb.prerelease) return 0;
  if (!pa.prerelease) return 1;
  if (!pb.prerelease) return -1;
  return pa.prerelease.localeCompare(pb.prerelease, 'en', { numeric: true });
}

export function compareVersionRecords(a, b) {
  const codeA = Number(a?.versionCode ?? 0);
  const codeB = Number(b?.versionCode ?? 0);
  if (codeA !== codeB) return codeB - codeA;
  return compareVersions(b?.version, a?.version);
}

export function sortVersionRecords(records) {
  return [...records].sort(compareVersionRecords);
}

export function latestVersionRecord(records) {
  if (!Array.isArray(records) || records.length === 0) return null;
  return sortVersionRecords(records)[0];
}
