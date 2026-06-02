import { getObjectiveColor } from '../colors';

export default function ObjectiveModal({
  modal, categories, objectives,
  onChange, onSave, onDelete, onClose, onStartPlace,
}) {
  const { mode, form } = modal;

  const updateForm = (patch) => {
    const newForm = { ...form, ...patch };
    // Recalculate color variant when category changes
    if (patch.categoryId && patch.categoryId !== form.categoryId) {
      const idx = objectives.filter(o => o.categoryId === patch.categoryId && o.id !== form.id).length;
      newForm.colorVariant = getObjectiveColor(patch.categoryId, idx, categories);
    }
    onChange({ ...modal, form: newForm });
  };

  const removeMarker = (i) =>
    updateForm({ markers: form.markers.filter((_, mi) => mi !== i) });

  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal">
        <h2>{mode === 'add' ? 'Nouvel objectif' : 'Éditer objectif'}</h2>

        <div className="field">
          <label>Nom *</label>
          <input
            value={form.name}
            onChange={e => updateForm({ name: e.target.value })}
            placeholder="Nom de l'objectif"
            autoFocus
            onKeyDown={e => e.key === 'Enter' && onSave()}
          />
        </div>

        <div className="field">
          <label>Description</label>
          <textarea
            rows={2}
            value={form.description || ''}
            onChange={e => updateForm({ description: e.target.value })}
            placeholder="Description optionnelle"
          />
        </div>

        <div className="field">
          <label>Catégorie</label>
          <select value={form.categoryId} onChange={e => updateForm({ categoryId: e.target.value })}>
            {categories.map(cat => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </select>
        </div>

        <div className="toggle-row">
          <label className="switch">
            <input
              type="checkbox"
              checked={form.anywhere}
              onChange={e => updateForm({ anywhere: e.target.checked })}
            />
            <span className="switch-slider" />
          </label>
          <span>Réalisable partout <span style={{ color: 'var(--text-dim)', fontSize: 11 }}>(apparaît dans la légende, pas sur la carte)</span></span>
        </div>

        {!form.anywhere && (
          <div className="field">
            <label>Emplacements sur la carte ({form.markers.length})</label>
            <div className="markers-list">
              {form.markers.length === 0 && (
                <div style={{ fontSize: 12, color: 'var(--text-dim)', padding: '2px 0 6px' }}>
                  Aucun emplacement — ajoutez-en un ci-dessous.
                </div>
              )}
              {form.markers.map((m, i) => (
                <div key={i} className="marker-row">
                  <span className="dot" style={{ background: form.colorVariant }} />
                  <span>x: {(m.x * 100).toFixed(1)}% &nbsp; y: {(m.y * 100).toFixed(1)}%</span>
                  <button
                    className="btn btn-ghost btn-sm"
                    style={{ marginLeft: 'auto', color: 'var(--danger)' }}
                    onClick={() => removeMarker(i)}
                  >
                    ✕
                  </button>
                </div>
              ))}
              <button className="btn btn-secondary btn-sm" style={{ marginTop: 4 }} onClick={onStartPlace}>
                + Cliquer sur la carte pour ajouter un emplacement
              </button>
            </div>
          </div>
        )}

        <div className="color-preview-row">
          <span>Couleur du marqueur :</span>
          <span className="color-swatch" style={{ background: form.colorVariant }} />
          <span style={{ fontFamily: 'monospace', fontSize: 11, color: 'var(--text-dim)' }}>
            {form.colorVariant}
          </span>
        </div>

        <div className="modal-actions">
          {mode === 'edit' && (
            <button
              className="btn btn-danger btn-sm"
              style={{ marginRight: 'auto' }}
              onClick={() => { if (confirm(`Supprimer "${form.name}" ?`)) onDelete(form.id); }}
            >
              Supprimer
            </button>
          )}
          <button className="btn btn-ghost" onClick={onClose}>Annuler</button>
          <button className="btn btn-primary" onClick={onSave} disabled={!form.name.trim()}>
            {mode === 'add' ? 'Ajouter' : 'Sauvegarder'}
          </button>
        </div>
      </div>
    </div>
  );
}
