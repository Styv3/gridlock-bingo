export default function Legend({ objectives }) {
  return (
    <div className="legend">
      <span className="legend-label">Partout :</span>
      {objectives.length === 0 ? (
        <span className="legend-empty">Aucun objectif "partout" actif</span>
      ) : (
        objectives.map(obj => (
          <span
            key={obj.id}
            className="legend-item"
            style={{ borderColor: obj.colorVariant + '99', color: obj.colorVariant }}
            title={obj.description || obj.name}
          >
            <span className="legend-dot" style={{ background: obj.colorVariant }} />
            {obj.name}
          </span>
        ))
      )}
    </div>
  );
}
