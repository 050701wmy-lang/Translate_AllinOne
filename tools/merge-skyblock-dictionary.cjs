const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const target = 'E:/Minecraft/.minecraft/versions/Skyblock/config/translate_allinone/dictionary/skyblock_items_zh.json';
const source = path.resolve('dictionary/skb_items_zh.json');
const out = path.resolve('dictionary/skyblock-community-merged');
const normalize = s => s.replace(/§[0-9a-fk-or]/gi, '').replace(/\u00a0/g, ' ').replace(/[‘’]/g, "'").replace(/[“”]/g, '"').replace(/\s+/g, ' ').trim().toLowerCase();
const fixBraces = s => s.replace(/\{+(d\d+)\}+/g, '{$1}');
const placeholders = s => [...s.matchAll(/\{([A-Za-z_][A-Za-z0-9_]*)\}/g)].map(m => m[1]).sort().join(',');
const originalBytes = fs.readFileSync(target);
const base = JSON.parse(originalBytes.toString('utf8').replace(/^\uFEFF/, ''));
const community = JSON.parse(fs.readFileSync(source, 'utf8').replace(/^\uFEFF/, ''));
const merged = {...base};
const seen = new Map(Object.keys(base).map(k => [normalize(k), k]));
const report = {source, target, originalSha256: crypto.createHash('sha256').update(originalBytes).digest('hex'), originalEntries: Object.keys(base).length, communityEntries: Object.keys(community).length, added: 0, braceRepairs: 0, preservedConflicts: [], skipped: []};
for (const [rawKey, rawValue] of Object.entries(community)) {
  if (typeof rawValue !== 'string') throw new Error('Expected flat dictionary: ' + rawKey);
  const key = fixBraces(rawKey), value = fixBraces(rawValue);
  const norm = normalize(key);
  if (seen.has(norm)) {
    if (merged[seen.get(norm)] !== value) report.preservedConflicts.push(key);
    continue;
  }
  if (!norm || !value.trim() || placeholders(key) !== placeholders(value) || /\{\{|\}\}/.test(key + value)) {
    report.skipped.push({key: rawKey, value: rawValue, reason: 'Empty text or incompatible placeholders'});
    continue;
  }
  if (key !== rawKey || value !== rawValue) report.braceRepairs++;
  seen.set(norm, key);
  merged[key] = value;
  report.added++;
}
report.totalEntries = Object.keys(merged).length;
for (const [key, value] of Object.entries(base)) {
  if (merged[key] !== value) throw new Error('Existing translation changed: ' + key);
}
fs.mkdirSync(out, {recursive: true});
fs.writeFileSync(path.join(out, 'skyblock_items_zh.json'), JSON.stringify(merged, null, 2) + '\n');
fs.writeFileSync(path.join(out, 'merge-report.json'), JSON.stringify(report, null, 2) + '\n');
JSON.parse(fs.readFileSync(path.join(out, 'skyblock_items_zh.json'), 'utf8'));
console.log(JSON.stringify({...report, preservedConflicts: report.preservedConflicts.length, skipped: report.skipped.length}, null, 2));
console.log('Skipped samples:', JSON.stringify(report.skipped.slice(0, 8), null, 2));
