import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';

interface ToastApi {
  show: (message: string, kind?: 'ok' | 'error') => void;
}

const ToastContext = createContext<ToastApi>({ show: () => {} });
export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; kind: 'ok' | 'error' } | null>(null);
  const timer = useRef<number | undefined>(undefined);

  const show = useCallback((message: string, kind: 'ok' | 'error' = 'ok') => {
    setToast({ message, kind });
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setToast(null), 3200);
  }, []);

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {toast && (
        <div className={`toast ${toast.kind === 'error' ? 'error' : ''}`} role="status" aria-live="polite">
          {toast.message}
        </div>
      )}
    </ToastContext.Provider>
  );
}
