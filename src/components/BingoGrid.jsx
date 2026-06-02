import { useState } from 'react';

export default function BingoGrid({ bingoGrid, objectives, onHover, onGridChange }) {
  const [dragIdx, setDragIdx] = useState(null);
  const [dragOverIdx, setDragOverIdx] = useState(null);

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

  return (
    <div className="bingo-grid">
      <div className="bingo-title">Bingo 5 × 5</div>
      <div className="bingo-cells">
        {cells.map(({ slot, objectiveId, obj }, i) => {
          const isDragging = dragIdx === i;
          const isDragOver = dragOverIdx === i;
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
          return (
            <div
              key={slot.slotId}
              className={`bingo-cell${isDragging ? ' dragging' : ''}${isDragOver ? ' drag-over' : ''}`}
              style={{ background: obj.colorVariant + '55', borderColor: obj.colorVariant + '99' }}
              draggable
              onDragStart={() => setDragIdx(i)}
              onDragOver={e => { e.preventDefault(); setDragOverIdx(i); }}
              onDrop={() => handleDrop(i)}
              onDragEnd={() => { setDragIdx(null); setDragOverIdx(null); }}
              onDragLeave={() => setDragOverIdx(null)}
              onMouseEnter={() => onHover(objectiveId)}
              onMouseLeave={() => onHover(null)}
              title={obj.name}
            >
              {label}
            </div>
          );
        })}
      </div>
    </div>
  );
}
