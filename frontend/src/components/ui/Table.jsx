import { EmptyState } from './EmptyState';
import { LuChevronLeft, LuChevronRight } from 'react-icons/lu';

// Gap entre el chevron y el texto: .pagination button no es flex (sin tocar App.css)
const chevronBtnStyle = { display: 'inline-flex', alignItems: 'center', gap: 'var(--space-xs)' };

/**
 * Table component with pagination and empty state.
 * @param {{columns: Array<{key: string, label: string, render?: Function, width?: string}>, data: any[], emptyMessage?: string, page?: number, totalPages?: number, onPageChange?: Function, loading?: boolean}} props
 */
export default function Table({
  columns,
  data,
  emptyMessage = 'No hay registros',
  page = 1,
  totalPages = 1,
  onPageChange,
  loading = false,
}) {
  if (loading) {
    return (
      <div className="table-wrapper">
        <div className="skeleton" style={{ height: 40 }} />
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="skeleton" style={{ height: 48, marginTop: 4 }} />
        ))}
      </div>
    );
  }

  if (data.length === 0) {
    return <EmptyState title="Sin resultados" message={emptyMessage} />;
  }

  return (
    <div>
      <div className="table-wrapper">
        <table className="table">
          <thead>
            <tr>
              {columns.map((col) => (
                <th key={col.key} style={col.width ? { width: col.width } : undefined}>
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((row, idx) => (
              <tr key={row.id ?? idx}>
                {columns.map((col) => (
                  <td key={col.key}>
                    {col.render ? col.render(row) : row[col.key]}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {onPageChange && totalPages > 1 && (
        <div className="pagination">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={page <= 1}
            aria-label="Página anterior"
            style={chevronBtnStyle}
          >
            <LuChevronLeft aria-hidden="true" />
            Anterior
          </button>
          {Array.from({ length: totalPages }).map((_, i) => (
            <button
              key={i}
              className={page === i + 1 ? 'active' : ''}
              onClick={() => onPageChange(i + 1)}
            >
              {i + 1}
            </button>
          ))}
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages}
            aria-label="Página siguiente"
            style={chevronBtnStyle}
          >
            Siguiente
            <LuChevronRight aria-hidden="true" />
          </button>
        </div>
      )}
    </div>
  );
}
