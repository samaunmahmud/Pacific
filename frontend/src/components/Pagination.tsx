export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  const pages = Array.from({ length: totalPages }, (_, i) => i).filter(
    (i) => i === 0 || i === totalPages - 1 || Math.abs(i - page) <= 2,
  );
  return (
    <nav className="pagination" aria-label="Pagination">
      <button disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Previous page">‹</button>
      {pages.map((p, idx) => (
        <span key={p} className="row" style={{ gap: 8 }}>
          {idx > 0 && p - pages[idx - 1] > 1 && <span aria-hidden="true">…</span>}
          <button className={p === page ? 'active' : ''} onClick={() => onChange(p)} aria-current={p === page ? 'page' : undefined}>
            {p + 1}
          </button>
        </span>
      ))}
      <button disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} aria-label="Next page">›</button>
    </nav>
  );
}
