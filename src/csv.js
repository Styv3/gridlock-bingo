function parseCSVLine(line) {
  const result = [];
  let cur = '';
  let inQ = false;
  for (const ch of line) {
    if (ch === '"') inQ = !inQ;
    else if (ch === ',' && !inQ) { result.push(cur.trim()); cur = ''; }
    else cur += ch;
  }
  result.push(cur.trim());
  return result;
}

export function importFromCSV(text, categories) {
  const lines = text.trim().split(/\r?\n/);
  if (lines.length < 2) throw new Error('CSV vide');
  const headers = parseCSVLine(lines[0]).map(h => h.toLowerCase().replace(/"/g, ''));
  const get = (fields, key) => {
    const i = headers.indexOf(key);
    return i >= 0 ? (fields[i] || '').replace(/^"|"$/g, '') : '';
  };
  return lines.slice(1).filter(l => l.trim()).map(line => {
    const f = parseCSVLine(line);
    const catName = get(f, 'category').toLowerCase();
    const cat = categories.find(c => c.name.toLowerCase() === catName);
    return {
      name: get(f, 'name'),
      description: get(f, 'description'),
      categoryId: cat?.id || categories[0]?.id || 'simple',
      anywhere: get(f, 'anywhere').toLowerCase() === 'true',
    };
  }).filter(o => o.name);
}

export function exportToCSV(objectives, categories) {
  const header = 'name,description,category,anywhere';
  const rows = objectives.map(o => {
    const cat = categories.find(c => c.id === o.categoryId);
    return [
      `"${(o.name || '').replace(/"/g, '""')}"`,
      `"${(o.description || '').replace(/"/g, '""')}"`,
      `"${(cat?.name || '').replace(/"/g, '""')}"`,
      o.anywhere ? 'true' : 'false',
    ].join(',');
  });
  return [header, ...rows].join('\n');
}
