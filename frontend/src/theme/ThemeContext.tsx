import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

export type Theme = 'light' | 'dark';
const KEY = 'pacific-theme';
const media = () => window.matchMedia('(prefers-color-scheme: dark)');

/** The shopper's saved choice, or null to follow the system setting. */
function savedChoice(): Theme | null {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'light' || v === 'dark' ? v : null;
  } catch {
    return null;
  }
}

type ThemeState = { theme: Theme; toggle: () => void };
const ThemeContext = createContext<ThemeState>({ theme: 'light', toggle: () => {} });

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [choice, setChoice] = useState<Theme | null>(savedChoice);
  const [systemDark, setSystemDark] = useState(() => media().matches);
  const theme: Theme = choice ?? (systemDark ? 'dark' : 'light');

  // until the shopper picks one, follow the system setting as it changes
  useEffect(() => {
    const m = media();
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    m.addEventListener('change', onChange);
    return () => m.removeEventListener('change', onChange);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  const toggle = useCallback(() => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark';
    setChoice(next);
    try {
      localStorage.setItem(KEY, next);
    } catch {
      /* storage blocked: the switch still works for this visit */
    }
  }, [theme]);

  return <ThemeContext.Provider value={{ theme, toggle }}>{children}</ThemeContext.Provider>;
}

export const useTheme = () => useContext(ThemeContext);
