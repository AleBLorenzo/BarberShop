import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { format, startOfMonth, endOfMonth } from 'date-fns';
import { adminApi } from '../../services/api';
import { formatCurrency } from '../../utils/date';
import PageHeader from '../../components/ui/PageHeader';
import DatePicker from '../../components/ui/DatePicker';
import Spinner from '../../components/ui/Spinner';
import { ErrorState } from '../../components/ui/EmptyState';
import { useSeo } from '../../components/Seo';

/**
 * Admin dashboard: metrics cards + top services chart + date range.
 */
export default function AdminDashboard() {
  // Panel privado: noindex (spec `seo-meta`; D2).
  useSeo({
    title: 'Panel de administración — TheTankBarber',
    description: 'Métricas y resumen de la actividad de TheTankBarber. Área privada.',
    noindex: true,
  });

  const [searchParams, setSearchParams] = useSearchParams();
  const startParam = searchParams.get('start');
  const endParam = searchParams.get('end');

  const [start, setStart] = useState(startParam || format(startOfMonth(new Date()), 'yyyy-MM-dd'));
  const [end, setEnd] = useState(endParam || format(endOfMonth(new Date()), 'yyyy-MM-dd'));
  const [stats, setStats] = useState(null);
  const [topServicios, setTopServicios] = useState([]);
  // Ingresos por trabajador (D10): mismo rango y criterio que las tarjetas
  const [ingresos, setIngresos] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async (startStr, endStr) => {
    try {
      const [statsData, topData, ingresosData] = await Promise.all([
        adminApi.getStats(startStr, endStr),
        adminApi.getTopServicios(startStr, endStr, 5),
        // Best-effort: si falla, el resto del dashboard sigue pintándose
        adminApi.getIngresos(startStr, endStr).catch(() => null),
      ]);
      setStats(statsData);
      setTopServicios(topData);
      setIngresos(ingresosData);
      setError(null);
      setLoading(false);
    } catch {
      setError('No se pudieron cargar las estadísticas.');
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load(start, end);
  }, [start, end, load]);

  // URL params updated in the event handlers (setSearchParams has unstable identity)
  const handleStartChange = (v) => {
    if (!v) return;
    setStart(v);
    setSearchParams({ start: v, end }, { replace: true });
  };
  const handleEndChange = (v) => {
    if (!v) return;
    setEnd(v);
    setSearchParams({ start, end: v }, { replace: true });
  };

  const maxCount = Math.max(...topServicios.map((s) => s.totalReservas), 1);

  return (
    <div>
      <PageHeader
        title="Dashboard"
        actions={
          <div className="filters-bar" style={{ marginBottom: 0 }}>
            <DatePicker label="Desde" value={start} onChange={handleStartChange} />
            <DatePicker label="Hasta" value={end} onChange={handleEndChange} />
          </div>
        }
      />

      {error && <ErrorState message={error} onRetry={() => load(start, end)} />}

      {loading ? (
        <div className="loading-page">
          <Spinner size="lg" />
          <p>Cargando métricas...</p>
        </div>
      ) : stats ? (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-label">Total reservas</div>
              <div className="stat-value">{stats.totalReservas}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Confirmadas</div>
              <div className="stat-value accent">{stats.reservasConfirmadas}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Completadas</div>
              <div className="stat-value success">{stats.reservasCompletadas}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Canceladas</div>
              <div className="stat-value danger">{stats.reservasCanceladas}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">No shows</div>
              <div className="stat-value warning">{stats.noShows}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Ingresos totales</div>
              <div className="stat-value">{formatCurrency(stats.ingresosTotales)}</div>
              {(stats.ingresosVisa != null || stats.ingresosEfectivo != null) && (
                <div className="stat-breakdown">
                  <span>VISA · {formatCurrency(stats.ingresosVisa ?? 0)}</span>
                  <span>Efectivo · {formatCurrency(stats.ingresosEfectivo ?? 0)}</span>
                </div>
              )}
            </div>
            <div className="stat-card">
              <div className="stat-label">Ingreso promedio</div>
              <div className="stat-value">{formatCurrency(stats.ingresoPromedioPorReserva)}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Tasa de ocupación</div>
              <div className="stat-value accent">{stats.tasaOcupacion}%</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Tasa no-show</div>
              <div className="stat-value warning">{stats.tasaNoShow}%</div>
            </div>
          </div>

          <div className="card admin-section">
            <div className="card-header">
              <h3>Ingresos por trabajador</h3>
            </div>
            <div className="card-body">
              {!ingresos ? (
                <p className="muted-text">No se pudieron cargar los ingresos por trabajador.</p>
              ) : !ingresos.filas?.length ? (
                <p className="muted-text">Sin datos en este periodo.</p>
              ) : (
                <div className="table-wrapper">
                  <table className="table ingresos-table">
                    <thead>
                      <tr>
                        <th scope="col">Trabajador</th>
                        <th scope="col">Total</th>
                        <th scope="col">VISA</th>
                        <th scope="col">Efectivo</th>
                        <th scope="col">Completadas</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ingresos.filas.map((f) => (
                        <tr key={f.trabajadorId ?? f.nombre}>
                          <td>
                            {f.nombre}
                            {f.rol === 'ADMIN' && <span className="muted-inline"> · admin</span>}
                            {f.activo === false && <span className="muted-inline"> · inactivo</span>}
                          </td>
                          <td>{formatCurrency(f.total ?? 0)}</td>
                          <td>{formatCurrency(f.visa ?? 0)}</td>
                          <td>{formatCurrency(f.efectivo ?? 0)}</td>
                          <td>{f.completadas ?? 0}</td>
                        </tr>
                      ))}
                    </tbody>
                    {ingresos.totales && (
                      <tfoot>
                        <tr>
                          <th scope="row">Total</th>
                          <td>{formatCurrency(ingresos.totales.total ?? 0)}</td>
                          <td>{formatCurrency(ingresos.totales.visa ?? 0)}</td>
                          <td>{formatCurrency(ingresos.totales.efectivo ?? 0)}</td>
                          <td>{ingresos.totales.completadas ?? 0}</td>
                        </tr>
                      </tfoot>
                    )}
                  </table>
                </div>
              )}
              <p className="hint-text">
                Total = completadas + confirmadas; VISA/efectivo solo de las completadas.
              </p>
            </div>
          </div>

          <div className="card">
            <div className="card-header">
              <h3>Servicios más solicitados</h3>
            </div>
            <div className="card-body">
              {topServicios.length === 0 ? (
                <p style={{ color: 'var(--muted)' }}>Sin datos en este periodo.</p>
              ) : (
                <div className="bar-chart" role="img" aria-label="Gráfico de servicios top">
                  {topServicios.map((s) => (
                    <div key={s.servicioId} className="bar-row">
                      <span className="bar-label">{s.servicioNombre}</span>
                      <div className="bar-track">
                        <div
                          className="bar-fill"
                          style={{ width: `${(s.totalReservas / maxCount) * 100}%` }}
                        />
                      </div>
                      <span className="bar-value">
                        {s.totalReservas} · {formatCurrency(s.ingresosGenerados)}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
