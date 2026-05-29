const PHI = 0.618033988749895;

export function getObjectiveColor(categoryId, indexInCategory, categories) {
  const cat = categories.find(c => c.id === categoryId);
  if (!cat) return 'hsl(0, 0%, 55%)';
  const spread = cat.spread ?? 40;
  const t = (indexInCategory * PHI) % 1;
  const hueOffset = t * spread - spread / 2;
  const hue = ((cat.hue + hueOffset) % 360 + 360) % 360;
  const sat = 65 + Math.round((indexInCategory * 13) % 25);
  const light = 45 + Math.round((indexInCategory * 7) % 15);
  return `hsl(${hue.toFixed(1)}, ${sat}%, ${light}%)`;
}

export function getCategoryBaseColor(cat) {
  if (!cat) return 'hsl(0, 0%, 55%)';
  return `hsl(${cat.hue}, 72%, 52%)`;
}
