import { useReducer, useEffect } from 'react';

const STORAGE_KEY = 'gridlock-bingo-v1';
const MAX_GRID_SIZE = 25;
const MAX_OBJECTIVE_COPIES = 2;
const QUEST_PLACEHOLDER_TYPE = 'quest-placeholder';

export const DEFAULT_CATEGORIES = [
  { id: 'simple',   name: 'Simple',   hue: 120, spread: 50 },
  { id: 'complex',  name: 'Complex',  hue: 280, spread: 45 },
  { id: 'team',     name: 'Team',     hue: 210, spread: 45 },
  { id: 'opponent', name: 'Opponent', hue: 0,   spread: 40 },
  { id: 'quest',    name: 'Quest',    hue: 32,  spread: 35 },
];

const DEFAULT_STATE = {
  categories: DEFAULT_CATEGORIES,
  objectives: [],
  activeObjectiveIds: [],
  bingoGrid: [],
};

function createGridSlot(objectiveId) {
  return { slotId: crypto.randomUUID(), objectiveId };
}

function createQuestPlaceholderSlot() {
  return { slotId: crypto.randomUUID(), type: QUEST_PLACEHOLDER_TYPE, label: 'Quest' };
}

function normalizeGridSlot(slot) {
  if (!slot) return null;
  if (typeof slot === 'string') return createGridSlot(slot);
  if (slot.objectiveId) return {
    slotId: slot.slotId || crypto.randomUUID(),
    objectiveId: slot.objectiveId,
  };
  if (slot.type === QUEST_PLACEHOLDER_TYPE) return {
    slotId: slot.slotId || crypto.randomUUID(),
    type: QUEST_PLACEHOLDER_TYPE,
    label: slot.label || 'Quest',
  };
  return null;
}

function normalizeBingoGrid(bingoGrid) {
  if (!Array.isArray(bingoGrid)) return [];
  return bingoGrid.map(normalizeGridSlot).filter(Boolean).slice(0, MAX_GRID_SIZE);
}

function normalizeState(data) {
  const state = { ...DEFAULT_STATE, ...data };
  const activeObjectiveIds = Array.isArray(state.activeObjectiveIds)
    ? [...new Set(state.activeObjectiveIds.filter(Boolean))]
    : [];

  return {
    ...state,
    categories: state.categories || DEFAULT_CATEGORIES,
    activeObjectiveIds,
    bingoGrid: normalizeBingoGrid(state.bingoGrid),
  };
}

function countObjectiveCopies(bingoGrid, objectiveId) {
  return bingoGrid.filter(slot => slot.objectiveId === objectiveId).length;
}

function isQuestObjective(objectives, objectiveId) {
  return objectives.some(o => o.id === objectiveId && o.categoryId === 'quest');
}

function replaceFirstQuestPlaceholder(bingoGrid, objectiveId) {
  const placeholderIdx = bingoGrid.findIndex(slot => slot.type === QUEST_PLACEHOLDER_TYPE);
  if (placeholderIdx < 0) return null;
  return bingoGrid.map((slot, i) => i === placeholderIdx ? createGridSlot(objectiveId) : slot);
}

function getInitialState() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved);
      return normalizeState(parsed);
    }
  } catch {
    return DEFAULT_STATE;
  }
  return DEFAULT_STATE;
}

function reducer(state, action) {
  switch (action.type) {
    case 'ADD_OBJECTIVE':
      return { ...state, objectives: [...state.objectives, action.payload] };

    case 'UPDATE_OBJECTIVE':
      return {
        ...state,
        objectives: state.objectives.map(o => o.id === action.payload.id ? action.payload : o),
      };

    case 'DELETE_OBJECTIVE': {
      const id = action.payload;
      return {
        ...state,
        objectives: state.objectives.filter(o => o.id !== id),
        activeObjectiveIds: state.activeObjectiveIds.filter(aid => aid !== id),
        bingoGrid: state.bingoGrid.filter(slot => slot.objectiveId !== id),
      };
    }

    case 'TOGGLE_ACTIVE': {
      const id = action.payload;
      const isActive = state.activeObjectiveIds.includes(id);
      if (isActive) {
        return {
          ...state,
          activeObjectiveIds: state.activeObjectiveIds.filter(aid => aid !== id),
          bingoGrid: state.bingoGrid.filter(slot => slot.objectiveId !== id),
        };
      }
      if (state.bingoGrid.length >= MAX_GRID_SIZE) {
        if (!isQuestObjective(state.objectives, id)) return state;
        const bingoGrid = replaceFirstQuestPlaceholder(state.bingoGrid, id);
        if (!bingoGrid) return state;
        return {
          ...state,
          activeObjectiveIds: [...state.activeObjectiveIds, id],
          bingoGrid,
        };
      }
      return {
        ...state,
        activeObjectiveIds: [...state.activeObjectiveIds, id],
        bingoGrid: [...state.bingoGrid, createGridSlot(id)],
      };
    }

    case 'ADD_GRID_PLACEMENT': {
      const id = action.payload;
      if (state.bingoGrid.length >= MAX_GRID_SIZE) return state;
      if (countObjectiveCopies(state.bingoGrid, id) >= MAX_OBJECTIVE_COPIES) return state;

      const activeObjectiveIds = state.activeObjectiveIds.includes(id)
        ? state.activeObjectiveIds
        : [...state.activeObjectiveIds, id];

      return {
        ...state,
        activeObjectiveIds,
        bingoGrid: [...state.bingoGrid, createGridSlot(id)],
      };
    }

    case 'ADD_QUEST_PLACEHOLDER':
      if (state.bingoGrid.length >= MAX_GRID_SIZE) return state;
      return {
        ...state,
        bingoGrid: [...state.bingoGrid, createQuestPlaceholderSlot()],
      };

    case 'SET_BINGO_GRID':
      return { ...state, bingoGrid: normalizeBingoGrid(action.payload) };

    case 'RESET_GAME':
      return { ...state, activeObjectiveIds: [], bingoGrid: [] };

    case 'ADD_CATEGORY':
      return { ...state, categories: [...state.categories, action.payload] };

    case 'IMPORT_DATA':
      return normalizeState(action.payload);

    default:
      return state;
  }
}

export function useStore() {
  const [state, dispatch] = useReducer(reducer, null, getInitialState);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Ignore storage failures so the app keeps running in restricted browsers.
    }
  }, [state]);

  return { state, dispatch };
}
