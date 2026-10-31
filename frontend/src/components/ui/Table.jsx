import { EmptyState } from './EmptyState';
import { LuChevronLeft, LuChevronRight } from 'react-icons/lu';

/**
 * Clase de la celda para la vista móvil en tarjetas (≤640px, ver `.table--stack`
 * en App.css): `title` = cabecera de la tarjeta, `hide` = se oculta en móvil,
 * la columna `actions` va al pie de la tarjeta a ancho completo.
 */
function cellClass(col) {
  if (col.key === 'actions') return 'cell-actions';
  if (col.mobile === 'title') return 'cell-title';
  if (col.mobile === 'hide') return 'cell-hide-mobile';
  return undefined;
}

/**
 * Table component with pagination and empty state.
 * En móvil cada fila se muestra como una tarjeta (etiqueta: valor) en vez de
 * desbordar en horizontal; en escritorio es una tabla normal.
 * @param {{columns: Array<{key: string, label: string, render?: Function, width?: string, mobile?: 'title'|'hide'}>, data: any[], emptyMessage?: string, page?: number, totalPages?: number, onPageChange?: Function, loading?: boolean}} props
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
      <div className="table-skeleton" aria-busy="true" aria-label="Cargando">
        <div className="skeleton table-skeleton-head" />
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="skeleton table-skeleton-row" />
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
        <table className="table table--stack">
          <thead>
            <tr>
              {columns.map((col) => (
                <th key={col.key} scope="col" style={col.width ? { width: col.width } : undefined}>
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((row, idx) => (
              <tr key={row.id ?? idx}>
                {columns.map((col) => (
                  <td key={col.key} data-label={col.label} className={cellClass(col)}>
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
          >
            <LuChevronLeft aria-hidden="true" />
            Anterior
          </button>
          {Array.from({ length: totalPages }).map((_, i) => (
            <button
              key={i}
              className={page === i + 1 ? 'active' : ''}
              onClick={() => onPageChange(i + 1)}
              aria-current={page === i + 1 ? 'page' : undefined}
              aria-label={`Página ${i + 1}`}
            >
              {i + 1}
            </button>
          ))}
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages}
            aria-label="Página siguiente"
          >
            Siguiente
            <LuChevronRight aria-hidden="true" />
          </button>
        </div>
      )}
    </div>
  );
}
