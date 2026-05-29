import { useReducer, useEffect } from 'react';

const STORAGE_KEY = 'gridlock-bingo-v1';

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

function getInitialState() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved);
      return { ...DEFAULT_STATE, ...parsed, categories: parsed.categories || DEFAULT_CATEGORIES };
    }
  } catch {}
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
        bingoGrid: state.bingoGrid.filter(gid => gid !== id),
      };
    }

    case 'TOGGLE_ACTIVE': {
      const id = action.payload;
      const isActive = state.activeObjectiveIds.includes(id);
      if (isActive) {
        return {
          ...state,
          activeObjectiveIds: state.activeObjectiveIds.filter(aid => aid !== id),
          bingoGrid: state.bingoGrid.filter(gid => gid !== id),
        };
      }
      if (state.activeObjectiveIds.length >= 25) return state;
      return {
        ...state,
        activeObjectiveIds: [...state.activeObjectiveIds, id],
        bingoGrid: [...state.bingoGrid, id],
      };
    }

    case 'SET_BINGO_GRID':
      return { ...state, bingoGrid: action.payload };

    case 'RESET_GAME':
      return { ...state, activeObjectiveIds: [], bingoGrid: [] };

    case 'ADD_CATEGORY':
      return { ...state, categories: [...state.categories, action.payload] };

    case 'IMPORT_DATA':
      return { ...DEFAULT_STATE, ...action.payload };

    default:
      return state;
  }
}

export function useStore() {
  const [state, dispatch] = useReducer(reducer, null, getInitialState);

  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch {}
  }, [state]);

  return { state, dispatch };
}
