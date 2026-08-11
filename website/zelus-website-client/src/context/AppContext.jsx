import { createContext, useContext, useState, useEffect } from 'react';

const AppContext = createContext(null);

function loadFromStorage(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

/**
 * Reads `?payment=success|cancelled` from the URL and strips those params so a
 * page refresh doesn't re-show the result screen.  Called once at module load.
 */
function consumePaymentResult() {
  const params = new URLSearchParams(window.location.search);
  const result = params.get('payment');
  if (result) {
    params.delete('payment');
    params.delete('session_id');
    const newSearch = params.toString();
    window.history.replaceState(
      {},
      '',
      window.location.pathname + (newSearch ? `?${newSearch}` : ''),
    );
  }
  return result ?? null;  // 'success' | 'cancelled' | null
}

// Run once at module load — before any React state is initialized.
const _initialPaymentResult = consumePaymentResult();

/** Maps URL pathname → view name */
const PATH_TO_VIEW = {
  '/':             'home',
  '/store':        'store',
  '/donate':       'store',
  '/vote':         'vote',
  '/hiscores':     'hiscores',
  '/download':     'download',
  '/staff-login':  'staff_login',
  '/admin':        'admin',
};

/** Maps view name → canonical URL path */
const VIEW_TO_PATH = {
  home:           '/',
  store:          '/store',
  vote:           '/vote',
  hiscores:       '/hiscores',
  download:       '/download',
  staff_login:    '/staff-login',
  admin:          '/admin',
  payment_result: '/',
};

function getInitialView() {
  if (_initialPaymentResult) return 'payment_result';
  const path = window.location.pathname;
  return PATH_TO_VIEW[path] ?? 'home';
}

// ── Game mode display map ─────────────────────────────────────────────────────
// Aligned with NR 288 GameMode enum.
// STANDARD replaces the old NORMAL value — NORMAL is kept as a legacy alias so
// any account that hasn't been migrated yet still renders correctly.
// Exported so any component can import it directly.
// eslint-disable-next-line react-refresh/only-export-components -- constant map, not a component; idiomatic to keep beside the context it labels
export const GAME_MODE_LABELS = {
  STANDARD:               { label: 'Standard',              icon: '⚔️'  },
  IRONMAN:                { label: 'Ironman',                icon: '🛡️'  },
  HARDCORE_IRONMAN:       { label: 'Hardcore Ironman',       icon: '💀'  },
  ULTIMATE_IRONMAN:       { label: 'Ultimate Ironman',       icon: '🔥'  },
  GROUP_IRONMAN:          { label: 'Group Ironman',          icon: '👥'  },
  HARDCORE_GROUP_IRONMAN: { label: 'Hardcore Group Ironman', icon: '☠️'  },
  NORMAL:                 { label: 'Standard',              icon: '⚔️'  }, // legacy alias
};

/**
 * Returns a { label, icon } object for a given GameMode string.
 * Defaults gracefully for unknown or null values.
 * Exported so any component can use it without importing the whole map.
 */
// eslint-disable-next-line react-refresh/only-export-components -- helper function, not a component
export const getGameModeLabel = (mode) =>
  GAME_MODE_LABELS[mode] ?? { label: mode ?? 'Standard', icon: '⚔️' };

// ── AppProvider ────────────────────────────────────────────────────────────────
export function AppProvider({ children }) {
  const [currentUser,   setCurrentUserState] = useState(() => loadFromStorage('currentUser', null));
  const [currentView,   setCurrentViewState] = useState(getInitialView);
  const [paymentResult, setPaymentResult]    = useState(_initialPaymentResult);
  const [authStatus,    setAuthStatus]       = useState({ type: '', message: '' });
  const [storeMessage,  setStoreMessage]     = useState({ type: '', message: '' });
  // When set to a package object, the CheckoutModal is open.
  const [checkoutPkg,   setCheckoutPkg]      = useState(null);

  const setCurrentUser = (user) => {
    setCurrentUserState(user);
    if (user) localStorage.setItem('currentUser', JSON.stringify(user));
    else {
      localStorage.removeItem('currentUser');
      localStorage.removeItem('currentView'); // clean up legacy key
    }
  };

  const setCurrentView = (view) => {
    setCurrentViewState(view);
    const path = VIEW_TO_PATH[view] ?? '/';
    window.history.pushState({ view }, '', path);
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  // Handle browser back/forward buttons
  useEffect(() => {
    const onPopState = (e) => {
      const path = window.location.pathname;
      const view = (e.state?.view) ?? PATH_TO_VIEW[path] ?? 'home';
      setCurrentViewState(view);
      window.scrollTo({ top: 0, behavior: 'instant' });
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  const handleLogout = () => {
    setCurrentUser(null);
    setCurrentView('home');
    setStoreMessage({ type: '', message: '' });
  };

  /**
   * Called by store cards and the featured widget.
   * Opens the checkout modal directly — no account required, just an
   * in-game username (collected inside CheckoutModal).
   */
  const handleCheckout = (pkg) => {
    setCheckoutPkg(pkg);
  };

  return (
    <AppContext.Provider value={{
      currentUser,    setCurrentUser,
      currentView,    setCurrentView,
      authStatus,     setAuthStatus,
      storeMessage,   setStoreMessage,
      handleLogout,
      handleCheckout,
      checkoutPkg,    setCheckoutPkg,
      paymentResult,  setPaymentResult,
    }}>
      {children}
    </AppContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components -- standard context+hook pattern, hook belongs with its provider
export const useApp = () => {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside <AppProvider>');
  return ctx;
};
