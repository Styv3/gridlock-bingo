import { useRef, useEffect, useState, useCallback } from 'react';
import { getCategoryBaseColor } from '../colors';

const MARKER_BASE = 16;
const MARKER_HOVER = 26;
const CLUSTER_DETECT = 4;  // distance (px) — seulement les marqueurs quasi-identiques (même coord)
const CLUSTER_OFFSET = 6;  // rayon d'écartement (px) pour les marqueurs superposés

function getImageLayout(img) {
  if (!img?.naturalWidth || !img?.clientWidth) return null;
  const scale = Math.min(img.clientWidth / img.naturalWidth, img.clientHeight / img.naturalHeight);
  const rw = img.naturalWidth * scale;
  const rh = img.naturalHeight * scale;
  return { rw, rh, ox: (img.clientWidth - rw) / 2, oy: (img.clientHeight - rh) / 2 };
}

function buildMarkerList(objectives, layout) {
  if (!layout) return [];
  const raw = objectives.flatMap(obj =>
    (obj.markers || []).map((m, mi) => ({
      key: `${obj.id}-${mi}`,
      objectiveId: obj.id,
      name: obj.name,
      color: obj.colorVariant,
      px: layout.ox + m.x * layout.rw,
      py: layout.oy + m.y * layout.rh,
    }))
  );
  // Group nearby markers and offset them in a circle
  const groups = [];
  const used = new Set();
  for (let i = 0; i < raw.length; i++) {
    if (used.has(i)) continue;
    const group = [raw[i]];
    for (let j = i + 1; j < raw.length; j++) {
      if (used.has(j)) continue;
      if (Math.hypot(raw[i].px - raw[j].px, raw[i].py - raw[j].py) < CLUSTER_DETECT * 2) {
        group.push(raw[j]);
        used.add(j);
      }
    }
    used.add(i);
    groups.push(group);
  }
  return groups.flatMap(group => {
    if (group.length === 1) return group;
    const cx = group.reduce((s, m) => s + m.px, 0) / group.length;
    const cy = group.reduce((s, m) => s + m.py, 0) / group.length;
    return group.map((m, i) => {
      const angle = (i / group.length) * 2 * Math.PI - Math.PI / 2;
      return { ...m, px: cx + Math.cos(angle) * CLUSTER_OFFSET, py: cy + Math.sin(angle) * CLUSTER_OFFSET };
    });
  });
}

export default function MapView({ objectives, categories, hoveredId, hoveredObjective, isPlacing, onMapClick, onCancelPlace }) {
  const imgRef = useRef(null);
  const containerRef = useRef(null);
  const [layout, setLayout] = useState(null);
  const [localHover, setLocalHover] = useState(null);
  const [imgLoaded, setImgLoaded] = useState(false);
  const [pinnedObj, setPinnedObj] = useState(null);

  const updateLayout = useCallback(() => {
    if (imgRef.current) setLayout(getImageLayout(imgRef.current));
  }, []);

  useEffect(() => {
    const obs = new ResizeObserver(updateLayout);
    if (imgRef.current) obs.observe(imgRef.current);
    return () => obs.disconnect();
  }, [updateLayout, imgLoaded]);

  const handleContainerClick = useCallback((e) => {
    if (!isPlacing || !layout || !containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;
    const x = (px - layout.ox) / layout.rw;
    const y = (py - layout.oy) / layout.rh;
    if (x >= 0 && x <= 1 && y >= 0 && y <= 1) onMapClick(x, y);
  }, [isPlacing, layout, onMapClick]);

  const markers = buildMarkerList(objectives, layout);

  const localHoveredObjectiveId = localHover ? markers.find(m => m.key === localHover)?.objectiveId : null;
  const localHoveredObj = localHoveredObjectiveId ? objectives.find(o => o.id === localHoveredObjectiveId) : null;
  const activeHoverObj = localHoveredObj || hoveredObjective;

  useEffect(() => {
    if (!activeHoverObj) return undefined;
    const frame = requestAnimationFrame(() => setPinnedObj(activeHoverObj));
    return () => cancelAnimationFrame(frame);
  }, [activeHoverObj]);

  const displayObj = activeHoverObj || pinnedObj;
  const displayCat = displayObj ? categories?.find(c => c.id === displayObj.categoryId) : null;
  const displayCatColor = displayCat ? getCategoryBaseColor(displayCat) : '#888';

  return (
    <div
      ref={containerRef}
      className={`map-container${isPlacing ? ' placing' : ''}`}
      onClick={handleContainerClick}
    >
      <img
        ref={imgRef}
        src={`${import.meta.env.BASE_URL}map.jpg`}
        alt="Carte Gridlock"
        className="map-image"
        onLoad={() => { setImgLoaded(true); updateLayout(); }}
        draggable={false}
      />

      {!imgLoaded && (
        <div className="map-placeholder">
          <p>Image de la carte introuvable.</p>
          <p>Sauvegardez la carte sous <code>public/map.jpg</code> dans le dossier du projet.</p>
        </div>
      )}

      {displayObj && !isPlacing && (
        <div className="map-popup">
          <div className="map-popup-header">
            <div className="map-popup-name">{displayObj.name}</div>
            <button className="map-popup-close" onClick={e => { e.stopPropagation(); setPinnedObj(null); }}>✕</button>
          </div>
          {displayCat && (
            <div className="map-popup-cat">
              <span className="map-popup-dot" style={{ background: displayCatColor }} />
              <span style={{ color: displayCatColor }}>{displayCat.name}</span>
            </div>
          )}
          {displayObj.description && (
            <div className="map-popup-desc">{displayObj.description}</div>
          )}
          {displayObj.anywhere && (
            <div className="map-popup-anywhere">Réalisable n'importe où sur la carte</div>
          )}
        </div>
      )}

      {isPlacing && (
        <div className="placement-banner">
          <span>Cliquez sur la carte pour placer un marqueur</span>
          <button
            className="btn btn-ghost btn-sm"
            onClick={e => { e.stopPropagation(); onCancelPlace(); }}
          >
            Annuler
          </button>
        </div>
      )}

      {markers.map(m => {
        const isHovered = hoveredId === m.objectiveId || localHover === m.key;
        const size = isHovered ? MARKER_HOVER : MARKER_BASE;
        return (
          <div
            key={m.key}
            className={`marker${isHovered ? ' highlighted' : ''}`}
            style={{
              left: m.px,
              top: m.py,
              width: size,
              height: size,
              background: m.color,
              pointerEvents: isPlacing ? 'none' : 'all',
            }}
            onMouseEnter={() => !isPlacing && setLocalHover(m.key)}
            onMouseLeave={() => setLocalHover(null)}
          >
            <div className="marker-tooltip">{m.name}</div>
          </div>
        );
      })}
    </div>
  );
}
