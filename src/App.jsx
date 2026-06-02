import { useState, useCallback } from 'react';
import { useStore } from './store';
import { getObjectiveColor } from './colors';
import { exportToCSV, importFromCSV } from './csv';
import Sidebar from './components/Sidebar';
import MapView from './components/MapView';
import BingoGrid from './components/BingoGrid';
import ObjectiveModal from './components/ObjectiveModal';
import Legend from './components/Legend';

function downloadFile(content, filename, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  URL.revokeObjectURL(url);
}

export default function App() {
  const { state, dispatch } = useStore();
  const { categories, objectives, activeObjectiveIds, bingoGrid } = state;
  const gridCount = bingoGrid.length;

  const [search, setSearch] = useState('');
  const [catFilter, setCatFilter] = useState(null);
  const [hoveredId, setHoveredId] = useState(null);
  const [modal, setModal] = useState(null);
  const [isPlacing, setIsPlacing] = useState(false);
  const [showImportExport, setShowImportExport] = useState(false);
  const [importText, setImportText] = useState('');
  const [importError, setImportError] = useState('');
  const [showBingo, setShowBingo] = useState(true);

  const buildNewForm = useCallback(() => {
    const defaultCat = categories[0];
    const idx = objectives.filter(o => o.categoryId === defaultCat?.id).length;
    return {
      id: crypto.randomUUID(),
      name: '',
      description: '',
      categoryId: defaultCat?.id || 'simple',
      anywhere: false,
      markers: [],
      colorVariant: getObjectiveColor(defaultCat?.id, idx, categories),
    };
  }, [categories, objectives]);

  const openAdd = () => setModal({ mode: 'add', form: buildNewForm() });
  const openEdit = (obj) => setModal({ mode: 'edit', form: { ...obj } });

  const handleMapClick = useCallback((x, y) => {
    if (!isPlacing) return;
    setModal(prev => ({
      ...prev,
      form: { ...prev.form, markers: [...prev.form.markers, { x, y }] },
    }));
    setIsPlacing(false);
  }, [isPlacing]);

  const saveObjective = () => {
    if (!modal?.form?.name?.trim()) return;
    dispatch({ type: modal.mode === 'add' ? 'ADD_OBJECTIVE' : 'UPDATE_OBJECTIVE', payload: modal.form });
    setModal(null);
    setIsPlacing(false);
  };

  const deleteObjective = (id) => {
    dispatch({ type: 'DELETE_OBJECTIVE', payload: id });
    setModal(null);
  };

  const handleImportCSV = () => {
    setImportError('');
    try {
      const rows = importFromCSV(importText, categories);
      rows.forEach((row, i) => {
        const idx = objectives.filter(o => o.categoryId === row.categoryId).length + i;
        dispatch({
          type: 'ADD_OBJECTIVE',
          payload: {
            id: crypto.randomUUID(),
            name: row.name,
            description: row.description,
            categoryId: row.categoryId,
            anywhere: row.anywhere,
            markers: [],
            colorVariant: getObjectiveColor(row.categoryId, idx, categories),
          },
        });
      });
      setShowImportExport(false);
      setImportText('');
    } catch (e) {
      setImportError(e.message);
    }
  };

  const handleImportJSON = () => {
    setImportError('');
    try {
      const data = JSON.parse(importText);
      if (!data.objectives) throw new Error('Champ "objectives" manquant');
      if (!confirm('Remplacer toutes les données existantes par ce fichier JSON ?')) return;
      dispatch({ type: 'IMPORT_DATA', payload: data });
      setShowImportExport(false);
      setImportText('');
    } catch (e) {
      setImportError(e.message);
    }
  };

  const activeObjectives = activeObjectiveIds.map(id => objectives.find(o => o.id === id)).filter(Boolean);
  const mapObjectives = activeObjectives.filter(o => !o.anywhere);
  const anywhereObjectives = activeObjectives.filter(o => o.anywhere);
  const hoveredObjective = hoveredId ? objectives.find(o => o.id === hoveredId) : null;

  return (
    <div className="app">
      <header className="app-header">
        <h1>Gridlock Bingo Prep</h1>
        <div className="header-count">
          <span className={gridCount === 25 ? 'count-full' : 'count-num'}>
            {gridCount}
          </span>
          <span className="count-label">/ 25</span>
        </div>
        <div className="spacer" />
        <button className="btn btn-ghost btn-sm" onClick={() => setShowBingo(v => !v)}>
          {showBingo ? 'Masquer grille' : 'Afficher grille'}
        </button>
        <button className="btn btn-ghost btn-sm" onClick={() => { setShowImportExport(true); setImportError(''); }}>
          Import / Export
        </button>
        <button
          className="btn btn-danger btn-sm"
          onClick={() => {
            if (confirm('Réinitialiser la sélection pour la prochaine partie ?\n(Les objectifs et leurs emplacements sont conservés.)'))
              dispatch({ type: 'RESET_GAME' });
          }}
        >
          Reset partie
        </button>
      </header>

      <div className="app-body">
        <Sidebar
          objectives={objectives}
          categories={categories}
          activeObjectiveIds={activeObjectiveIds}
          bingoGrid={bingoGrid}
          hoveredId={hoveredId}
          search={search}
          catFilter={catFilter}
          onSearch={setSearch}
          onCatFilter={setCatFilter}
          onToggle={id => dispatch({ type: 'TOGGLE_ACTIVE', payload: id })}
          onAddGridPlacement={id => dispatch({ type: 'ADD_GRID_PLACEMENT', payload: id })}
          onEdit={openEdit}
          onAdd={openAdd}
          onAddCategory={name => {
            const hue = Math.floor(Math.random() * 360);
            dispatch({ type: 'ADD_CATEGORY', payload: { id: crypto.randomUUID(), name, hue, spread: 40 } });
          }}
          onHover={setHoveredId}
        />

        <div className="map-area">
          <MapView
            objectives={mapObjectives}
            categories={categories}
            hoveredId={hoveredId}
            hoveredObjective={hoveredObjective}
            isPlacing={isPlacing}
            onMapClick={handleMapClick}
            onCancelPlace={() => setIsPlacing(false)}
          />
          {showBingo && (
            <BingoGrid
              bingoGrid={bingoGrid}
              objectives={objectives}
              categories={categories}
              onHover={setHoveredId}
              onAddQuestPlaceholder={() => dispatch({ type: 'ADD_QUEST_PLACEHOLDER' })}
              onGridChange={grid => dispatch({ type: 'SET_BINGO_GRID', payload: grid })}
            />
          )}
        </div>
      </div>

      <footer className="app-footer">
        <Legend objectives={anywhereObjectives} categories={categories} />
      </footer>

      {modal && !isPlacing && (
        <ObjectiveModal
          modal={modal}
          categories={categories}
          objectives={objectives}
          onChange={setModal}
          onSave={saveObjective}
          onDelete={deleteObjective}
          onClose={() => { setModal(null); setIsPlacing(false); }}
          onStartPlace={() => setIsPlacing(true)}
        />
      )}

      {showImportExport && (
        <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowImportExport(false)}>
          <div className="modal">
            <h2>Import / Export</h2>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <button className="btn btn-secondary btn-sm" onClick={() => downloadFile(JSON.stringify(state, null, 2), 'gridlock-bingo.json', 'application/json')}>
                ↓ Exporter JSON (tout)
              </button>
              <button className="btn btn-secondary btn-sm" onClick={() => downloadFile(exportToCSV(objectives, categories), 'gridlock-objectifs.csv', 'text/csv;charset=utf-8')}>
                ↓ Exporter CSV (objectifs)
              </button>
            </div>
            <div className="field">
              <label>Importer — coller du JSON ou du CSV</label>
              <textarea
                style={{ height: 160, fontFamily: 'monospace', fontSize: 11, resize: 'vertical' }}
                placeholder={'Format CSV :\nname,description,category,anywhere\n"Mine Diamond","Miner un diamant",Simple,false'}
                value={importText}
                onChange={e => { setImportText(e.target.value); setImportError(''); }}
              />
            </div>
            {importError && <div className="import-error">Erreur : {importError}</div>}
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setShowImportExport(false)}>Fermer</button>
              <button className="btn btn-secondary" onClick={handleImportCSV} disabled={!importText.trim()}>
                ↑ Importer CSV (ajoute)
              </button>
              <button className="btn btn-primary" onClick={handleImportJSON} disabled={!importText.trim()}>
                ↑ Importer JSON (remplace tout)
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
