import { getCategoryBaseColor } from '../colors';

export default function Sidebar({
  objectives, categories, activeObjectiveIds, bingoGrid, hoveredId,
  search, catFilter, onSearch, onCatFilter,
  onToggle, onAddGridPlacement, onEdit, onAdd, onAddCategory, onHover,
}) {
  const filtered = objectives.filter(o => {
    const q = search.toLowerCase();
    const matchSearch = !q || o.name.toLowerCase().includes(q) || (o.description || '').toLowerCase().includes(q);
    const matchCat = !catFilter || o.categoryId === catFilter;
    return matchSearch && matchCat;
  });

  const handleAddCategory = () => {
    const name = prompt('Nom de la nouvelle catégorie :');
    if (name?.trim()) onAddCategory(name.trim());
  };

  const maxReached = bingoGrid.length >= 25;
  const hasQuestPlaceholder = bingoGrid.some(slot => slot.type === 'quest-placeholder');

  return (
    <aside className="sidebar">
      <div className="sidebar-top">
        <input
          className="search-input"
          placeholder="Rechercher un objectif..."
          value={search}
          onChange={e => onSearch(e.target.value)}
        />
        <div className="cat-filters">
          <span
            className={`cat-badge ${!catFilter ? 'active' : ''}`}
            style={{ background: 'rgba(255,255,255,0.1)', color: '#ccc' }}
            onClick={() => onCatFilter(null)}
          >
            Tous ({objectives.length})
          </span>
          {categories.map(cat => {
            const base = getCategoryBaseColor(cat);
            return (
              <span
                key={cat.id}
                className={`cat-badge ${catFilter === cat.id ? 'active' : ''}`}
                style={{
                  background: base + '28',
                  color: base,
                  borderColor: catFilter === cat.id ? base : 'transparent',
                }}
                onClick={() => onCatFilter(catFilter === cat.id ? null : cat.id)}
              >
                {cat.name} ({objectives.filter(o => o.categoryId === cat.id).length})
              </span>
            );
          })}
          <span
            className="cat-badge"
            style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--text-dim)', cursor: 'pointer' }}
            onClick={handleAddCategory}
          >
            + Catégorie
          </span>
        </div>
      </div>

      <div className="sidebar-list">
        {filtered.length === 0 ? (
          <div className="sidebar-empty">
            {objectives.length === 0
              ? 'Aucun objectif. Cliquez sur "+ Ajouter".'
              : 'Aucun résultat pour cette recherche.'}
          </div>
        ) : (
          filtered.map(obj => {
            const cat = categories.find(c => c.id === obj.categoryId);
            const isActive = activeObjectiveIds.includes(obj.id);
            const gridCopies = bingoGrid.filter(slot => slot.objectiveId === obj.id).length;
            const canReplaceQuestPlaceholder = !isActive && obj.categoryId === 'quest' && hasQuestPlaceholder;
            const isDisabled = !isActive && maxReached && !canReplaceQuestPlaceholder;
            const canAddCopy = isActive && gridCopies < 2 && !maxReached;
            const addCopyTitle = gridCopies === 0
              ? 'Ajouter dans la grille'
              : 'Ajouter une 2e occurrence dans la grille';
            const isHighlighted = hoveredId === obj.id;
            return (
              <div
                key={obj.id}
                className={`obj-item${isHighlighted ? ' highlighted' : ''}`}
                onMouseEnter={() => onHover(obj.id)}
                onMouseLeave={() => onHover(null)}
              >
                <input
                  type="checkbox"
                  className="obj-checkbox"
                  checked={isActive}
                  disabled={isDisabled}
                  onChange={() => onToggle(obj.id)}
                  onClick={e => e.stopPropagation()}
                  title={isDisabled ? 'Grille pleine' : canReplaceQuestPlaceholder ? 'Remplacera le premier placeholder Quest' : ''}
                />
                <span className="obj-dot" style={{ background: obj.colorVariant }} />
                <span className="obj-name" title={obj.name}>{obj.name}</span>
                {gridCopies > 1 && (
                  <span className="obj-grid-count" title={`${gridCopies} occurrences dans la grille`}>
                    x{gridCopies}
                  </span>
                )}
                {isActive && gridCopies < 2 && (
                  <button
                    className="obj-grid-add-btn"
                    onClick={e => { e.stopPropagation(); onAddGridPlacement(obj.id); }}
                    disabled={!canAddCopy}
                    title={maxReached ? 'Grille pleine' : addCopyTitle}
                  >
                    +
                  </button>
                )}
                {obj.anywhere && <span className="anywhere-tag" title="Partout">∞</span>}
                {cat && (
                  <span
                    className="obj-cat-tag"
                    style={{ background: getCategoryBaseColor(cat) + '22', color: getCategoryBaseColor(cat) }}
                  >
                    {cat.name}
                  </span>
                )}
                <button
                  className="obj-edit-btn"
                  onClick={e => { e.stopPropagation(); onEdit(obj); }}
                  title="Éditer"
                >
                  ✏
                </button>
              </div>
            );
          })
        )}
      </div>

      <div className="sidebar-bottom">
        <button className="btn btn-primary btn-sm" onClick={onAdd}>+ Ajouter</button>
        {maxReached && (
          <span style={{ fontSize: 11, color: 'var(--accent)', fontWeight: 600 }}>25/25 ✓</span>
        )}
      </div>
    </aside>
  );
}
