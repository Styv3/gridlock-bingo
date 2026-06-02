import { useState } from 'react';

const QUEST_PLACEHOLDER_TYPE = 'quest-placeholder';

function getCategoryCellStyle(obj, categories) {
  const cat = categories.find(c => c.id === obj.categoryId);
  const hue = cat?.hue ?? 0;
  return {
    '--bingo-category-bg': `linear-gradient(180deg, hsla(${hue}, 72%, 52%, 0.42), hsla(${hue}, 72%, 34%, 0.32))`,
    '--bingo-category-border': `hsla(${hue}, 76%, 66%, 0.78)`,
    '--bingo-category-inner': `hsla(${hue}, 76%, 72%, 0.2)`,
  };
}

export default function BingoGrid({
  bingoGrid,
  objectives,
  categories,
  onHover,
  onAddQuestPlaceholder,
  onGridChange,
}) {
  const [dragIdx, setDragIdx] = useState(null);
  const [dragOverIdx, setDragOverIdx] = useState(null);
  const canAddQuestPlaceholder = bingoGrid.length < 25;

  const cells = Array.from({ length: 25 }, (_, i) => {
    const slot = bingoGrid[i] ?? null;
    const objectiveId = slot?.objectiveId ?? null;
    return {
      slot,
      objectiveId,
      obj: objectiveId ? (objectives.find(o => o.id === objectiveId) ?? null) : null,
    };
  });

  const handleDrop = (targetIdx) => {
    if (dragIdx === null || dragIdx === targetIdx) return;
    const newGrid = Array.from({ length: 25 }, (_, i) => bingoGrid[i] ?? null);
    [newGrid[dragIdx], newGrid[targetIdx]] = [newGrid[targetIdx], newGrid[dragIdx]];
    onGridChange(newGrid.filter(Boolean));
    setDragIdx(null);
    setDragOverIdx(null);
  };

  const handleRemoveSlot = (slotId) => {
    onGridChange(bingoGrid.filter(slot => slot.slotId !== slotId));
  };

  return (
    <div className="bingo-grid">
      <div className="bingo-quick-add">
        <button
          className="bingo-quest-add"
          onClick={onAddQuestPlaceholder}
          disabled={!canAddQuestPlaceholder}
          title={canAddQuestPlaceholder ? 'Ajouter un placeholder Quest' : 'Grille pleine'}
        >
          + Quest
        </button>
      </div>
      <div className="bingo-title">Bingo 5 × 5</div>
      <div className="bingo-cells">
        {cells.map(({ slot, objectiveId, obj }, i) => {
          const isDragging = dragIdx === i;
          const isDragOver = dragOverIdx === i;
          if (slot?.type === QUEST_PLACEHOLDER_TYPE) {
            return (
              <div
                key={slot.slotId}
                className={`bingo-cell bingo-cell-quest${isDragging ? ' dragging' : ''}${isDragOver ? ' drag-over' : ''}`}
                draggable
                onDragStart={() => setDragIdx(i)}
                onDragOver={e => { e.preventDefault(); setDragOverIdx(i); }}
                onDrop={() => handleDrop(i)}
                onDragEnd={() => { setDragIdx(null); setDragOverIdx(null); }}
                onDragLeave={() => setDragOverIdx(null)}
                title="Placeholder Quest"
              >
                <button
                  className="bingo-cell-remove"
                  onClick={e => { e.stopPropagation(); handleRemoveSlot(slot.slotId); }}
                  onMouseDown={e => e.stopPropagation()}
                  title="Retirer le placeholder Quest"
                >
                  x
                </button>
                {slot.label || 'Quest'}
              </div>
            );
          }
          if (!obj) {
            return (
              <div
                key={i}
                className={`bingo-cell empty${isDragOver ? ' drag-over' : ''}`}
                onDragOver={e => { e.preventDefault(); setDragOverIdx(i); }}
                onDrop={() => handleDrop(i)}
                onDragLeave={() => setDragOverIdx(null)}
              >
                —
              </div>
            );
          }
          const label = obj.name.length > 18 ? obj.name.slice(0, 16) + '…' : obj.name;
          const cat = categories.find(c => c.id === obj.categoryId);
          return (
            <div
              key={slot.slotId}
              className={`bingo-cell bingo-cell-objective${isDragging ? ' dragging' : ''}${isDragOver ? ' drag-over' : ''}`}
              style={getCategoryCellStyle(obj, categories)}
              draggable
              onDragStart={() => setDragIdx(i)}
              onDragOver={e => { e.preventDefault(); setDragOverIdx(i); }}
              onDrop={() => handleDrop(i)}
              onDragEnd={() => { setDragIdx(null); setDragOverIdx(null); }}
              onDragLeave={() => setDragOverIdx(null)}
              onMouseEnter={() => onHover(objectiveId)}
              onMouseLeave={() => onHover(null)}
              title={cat ? `${obj.name} (${cat.name})` : obj.name}
            >
              {label}
            </div>
          );
        })}
      </div>
    </div>
  );
}
