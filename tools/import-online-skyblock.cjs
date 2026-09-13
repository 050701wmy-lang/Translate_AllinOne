const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const out = 'dictionary/skyblock-online-merged';
const target = 'E:/Minecraft/.minecraft/versions/Skyblock/config/translate_allinone/dictionary/skyblock_items_zh.json';
const bytes = fs.readFileSync(target);
const base = JSON.parse(bytes);
const strip = s => s.replace(/§[0-9a-fk-or]/gi, '');
const norm = s => strip(s).replace(/[‘’]/g, "'").replace(/[“”]/g, '"').replace(/\s+/g, ' ').trim().toLowerCase();
const ph = s => [...s.matchAll(/\{([^{}]+)\}/g)].map(x => x[1]).sort().join('|');
const merged = {...base}, index = new Map(Object.keys(base).map(k => [norm(k),k]));
const added = [], conflicts = [], skipped = {};
const skip = reason => skipped[reason] = (skipped[reason] || 0) + 1;
function add(key, value, source) {
  key = strip(key).trim();
  if (!key || !/[\u3400-\u9fff]/.test(value) || /[\r\n]/.test(key + value) || ph(key) !== ph(value) || /%(?:\d+\$)?[a-z]|\{\d+\}/i.test(key+value)) return skip('invalid_or_untranslated');
  if (/^[\s\p{P}\p{S}\d{}]+$/u.test(key)) return skip('overly_generic');
  if (index.has(norm(key))) {
    if (norm(merged[index.get(norm(key))]) !== norm(value)) conflicts.push({key, source});
    return;
  }
  index.set(norm(key), key); merged[key] = value; added.push({key, value, source});
}
const root = 'dictionary/upstream/SkyBlockZH/original_text';
function visit(node, file, blocked = false) {
  if (!node || typeof node !== 'object') return;
  if (Array.isArray(node)) {
    node.forEach((n,i) => visit(n,file,blocked || node[i+1]?.continuation === true)); return;
  }
  if (typeof node.text === 'string' && ('zh' in node || node.segments)) {
    if (blocked || node.continuation || node.translate === false || node.order) return skip('context_or_continuation');
    let value = node.zh;
    if (Array.isArray(node.segments)) {
      if (node.segments.some(s => typeof s.zh !== 'string' || !s.zh || s.order) || node.segments.map(s => s.text).join('') !== node.text) return skip('complex_segments');
      value = node.segments.map(s => (s.color || '') + s.zh).join('');
    } else if (typeof value === 'string') value = (node.raw?.match(/^(?:§[0-9a-fk-or])+/i)?.[0] || '') + value;
    if (typeof value !== 'string') return;
    let key = node.text;
    const tokens = key.match(/%s/g) || [];
    if (tokens.length) {
      if (tokens.length !== 1 || node.placeholders?.length !== 1 || node.placeholders[0].type !== 'number' || (value.match(/%s/g)||[]).length !== 1) return skip('unsupported_capture');
      key = key.replace('%s','{d1}'); value = value.replace('%s','{d1}');
    }
    add(key,value,'SkyBlockZH/' + file + '#' + (node.id || '')); return;
  }
  for (const [k,v] of Object.entries(node)) if (!['segments','placeholders','terms'].includes(k)) visit(v,file,blocked);
}
for (const file of fs.readdirSync(root,{recursive:true}).filter(f => f.endsWith('.json') && (/[/\\]GUI_Item[/\\]/.test(f) || f.startsWith('_shared'))).sort()) {
  const data = JSON.parse(fs.readFileSync(path.join(root,file),'utf8'));
  if (Array.isArray(data.terms)) for (const term of data.terms) if (term.en && term.zh && !term.en.includes('%')) add(term.en,term.zh,'SkyBlockZH/'+file);
  visit(data,file);
}
const second = JSON.parse(fs.readFileSync('dictionary/skyblock-online-merged/shoebox-simplified.json','utf8'));
function flatten(obj, source) {
  for (const [k,v] of Object.entries(obj)) {
    if (k.startsWith('_') || k === 'placeholders') continue;
    if (v && typeof v === 'object' && !Array.isArray(v)) flatten(v,source+'/'+k);
    else if (typeof v === 'string') {
      // Only literal entries: named placeholders require the source engine's vocabulary constraints.
      if (/[{}%]/.test(k+v)) {skip('shoebox_engine_template');continue;}
      add(k,v,source);
    }
  }
}
for (const [file,obj] of Object.entries(second)) flatten(obj,'ShoeBox-CX/'+file);
for (const [k,v] of Object.entries(base)) if (merged[k] !== v) throw Error('Original changed: '+k);
fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'skyblock_items_zh.json'),JSON.stringify(merged,null,2)+'\n');
const report = {originalSha256:crypto.createHash('sha256').update(bytes).digest('hex'), originalEntries:Object.keys(base).length,totalEntries:Object.keys(merged).length,added:added.length,bySource: Object.fromEntries(['SkyBlockZH','ShoeBox-CX'].map(s=>[s,added.filter(a=>a.source.startsWith(s)).length])),skipped,conflicts:conflicts.length};
fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({summary:report,added,conflicts},null,2)+'\n');
console.log(JSON.stringify(report,null,2));
console.log(JSON.stringify(added.filter(x=>/Hyperion|Aspect of the End|Mithril|Recombobulator|Dungeon|Drill/.test(x.key)).slice(0,15),null,2));
