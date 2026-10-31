import { useState, useEffect, useCallback } from 'react';
import { adminApi } from '../../services/api';
import PageHeader from '../../components/ui/PageHeader';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { Select } from '../../components/ui/Input';
import { ErrorState } from '../../components/ui/EmptyState';
import ConflictosList from '../../components/admin/ConflictosList';
import { useToast } from '../../hooks/useToast';
import { useSeo } from '../../components/Seo';
import { DIAS_SEMANA, hhmm, toApiTime } from '../../utils/horario';
import { LuRefreshCw } from 'react-icons/lu';

/** Modo de un día en el horario del trabajador. */
const HEREDA = 'HEREDA';
const LIBRE = 'LIBRE';
const PROPIO = 'PROPIO';

/** Lista del backend → mapa editable de los 7 días del horario general. */
const generalToForm = (dias) => {
  const porDia = new Map(dias.map((d) => [d.diaSemana, d]));
  return DIAS_SEMANA.map(({ key }) => {
    const d = porDia.get(key);
    return {
      diaSemana: key,
      abierto: !!d?.abierto,
      horaInicio: hhmm(d?.horaInicio) || '09:00',
      horaFin: hhmm(d?.horaFin) || '20:00',
    };
  });
};

/** Overrides del backend (solo días con horario propio) → 7 filas editables. */
const trabajadorToForm = (dias, general) => {
  const porDia = new Map(dias.map((d) => [d.diaSemana, d]));
  return DIAS_SEMANA.map(({ key }, i) => {
    const d = porDia.get(key);
    const g = general[i];
    if (!d) {
      return { diaSemana: key, modo: HEREDA, horaInicio: g.horaInicio, horaFin: g.horaFin };
    }
    return {
      diaSemana: key,
      modo: d.abierto ? PROPIO : LIBRE,
      horaInicio: hhmm(d.horaInicio) || g.horaInicio,
      horaFin: hhmm(d.horaFin) || g.horaFin,
    };
  });
};

/**
 * Admin: horario general de la barbería (7 días) y horario por trabajador
 * (hereda / día libre / horas propias). Al guardar, las citas que queden fuera
 * NO se cancelan: se listan como conflictos con "Cancelar y avisar" (D1, D12).
 */
export default function AdminHorarios() {
  useSeo({
    title: 'Horarios (admin) — TheTankBarber',
    description: 'Horario semanal de la barbería y de cada trabajador. Área privada.',
    noindex: true,
  });

  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [general, setGeneral] = useState([]);
  const [savingGeneral, setSavingGeneral] = useState(false);

  const [trabajadores, setTrabajadores] = useState([]);
  const [trabajadorId, setTrabajadorId] = useState('');
  const [horarioTrab, setHorarioTrab] = useState(null);
  const [loadingTrab, setLoadingTrab] = useState(false);
  const [savingTrab, setSavingTrab] = useState(false);

  const [conflictos, setConflictos] = useState([]);
  const [loadingConflictos, setLoadingConflictos] = useState(false);
  const [avisos, setAvisos] = useState([]);

  const loadConflictos = useCallback(async () => {
    setLoadingConflictos(true);
    try {
      setConflictos(await adminApi.getConflictos());
    } catch {
      toast.error('No se pudieron cargar las citas en conflicto');
    } finally {
      setLoadingConflictos(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const load = useCallback(async () => {
    try {
      const [dias, trabs, confl] = await Promise.all([
        adminApi.getHorario(),
        adminApi.getTrabajadores(),
        adminApi.getConflictos(),
      ]);
      setGeneral(generalToForm(dias));
      setTrabajadores(trabs.filter((t) => t.activo));
      setConflictos(confl);
      setError(null);
    } catch {
      setError('No se pudo cargar el horario.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
  }, [load]);

  // --- Horario general ---
  const setDiaGeneral = (i, patch) =>
    setGeneral((prev) => prev.map((d, j) => (j === i ? { ...d, ...patch } : d)));

  const guardarGeneral = async () => {
    const invalido = general.find((d) => d.abierto && (!d.horaInicio || !d.horaFin || d.horaFin <= d.horaInicio));
    if (invalido) {
      const label = DIAS_SEMANA.find((x) => x.key === invalido.diaSemana)?.label;
      toast.error(`${label}: la hora de cierre debe ser posterior a la de apertura`);
      return;
    }
    setSavingGeneral(true);
    try {
      const res = await adminApi.saveHorario(
        general.map((d) => ({
          diaSemana: d.diaSemana,
          abierto: d.abierto,
          horaInicio: d.abierto ? toApiTime(d.horaInicio) : null,
          horaFin: d.abierto ? toApiTime(d.horaFin) : null,
        }))
      );
      if (Array.isArray(res?.dias)) setGeneral(generalToForm(res.dias));
      setAvisos(Array.isArray(res?.avisos) ? res.avisos : []);
      const n = res?.conflictos?.length || 0;
      toast.success(n > 0 ? `Horario guardado · ${n} cita(s) quedan fuera` : 'Horario guardado');
      await loadConflictos();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo guardar el horario');
    } finally {
      setSavingGeneral(false);
    }
  };

  // --- Horario por trabajador ---
  const seleccionarTrabajador = async (id) => {
    setTrabajadorId(id);
    setHorarioTrab(null);
    if (!id) return;
    setLoadingTrab(true);
    try {
      const dias = await adminApi.getHorarioTrabajador(id);
      setHorarioTrab(trabajadorToForm(dias, general));
    } catch {
      toast.error('No se pudo cargar el horario del trabajador');
    } finally {
      setLoadingTrab(false);
    }
  };

  const setDiaTrab = (i, patch) =>
    setHorarioTrab((prev) => prev.map((d, j) => (j === i ? { ...d, ...patch } : d)));

  const guardarTrabajador = async () => {
    const propios = horarioTrab.filter((d) => d.modo === PROPIO);
    const invalido = propios.find((d) => !d.horaInicio || !d.horaFin || d.horaFin <= d.horaInicio);
    if (invalido) {
      const label = DIAS_SEMANA.find((x) => x.key === invalido.diaSemana)?.label;
      toast.error(`${label}: la hora de fin debe ser posterior a la de inicio`);
      return;
    }
    setSavingTrab(true);
    try {
      // Día ausente = hereda el general
      const payload = horarioTrab
        .filter((d) => d.modo !== HEREDA)
        .map((d) => ({
          diaSemana: d.diaSemana,
          abierto: d.modo === PROPIO,
          horaInicio: d.modo === PROPIO ? toApiTime(d.horaInicio) : null,
          horaFin: d.modo === PROPIO ? toApiTime(d.horaFin) : null,
        }));
      const res = await adminApi.saveHorarioTrabajador(trabajadorId, payload);
      if (Array.isArray(res?.dias)) setHorarioTrab(trabajadorToForm(res.dias, general));
      setAvisos(Array.isArray(res?.avisos) ? res.avisos : []);
      const n = res?.conflictos?.length || 0;
      toast.success(n > 0 ? `Horario guardado · ${n} cita(s) quedan fuera` : 'Horario del trabajador guardado');
      await loadConflictos();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo guardar el horario del trabajador');
    } finally {
      setSavingTrab(false);
    }
  };

  if (loading) {
    return (
      <div className="loading-page">
        <Spinner size="lg" />
        <p>Cargando horario...</p>
      </div>
    );
  }

  if (error) {
    return <ErrorState message={error} onRetry={() => { setLoading(true); load(); }} />;
  }

  return (
    <div>
      <PageHeader title="Horarios" />

      {/* Horario general */}
      <section className="card admin-section" aria-labelledby="horario-general-title">
        <div className="card-header">
          <h3 id="horario-general-title">Horario de la barbería</h3>
        </div>
        <div className="card-body">
          <p className="hint-text">
            Reducir el horario no cancela citas: las que queden fuera aparecen abajo para que decidas.
          </p>
          <div className="horario-list">
            {general.map((d, i) => {
              const dia = DIAS_SEMANA[i];
              return (
                <div key={d.diaSemana} className={`horario-row ${d.abierto ? '' : 'is-closed'}`}>
                  <span className="horario-dia">{dia.label}</span>
                  <label className="horario-toggle">
                    <input
                      type="checkbox"
                      checked={d.abierto}
                      onChange={(e) => setDiaGeneral(i, { abierto: e.target.checked })}
                    />
                    {d.abierto ? 'Abierto' : 'Cerrado'}
                  </label>
                  <div className="horario-horas">
                    <input
                      type="time"
                      className="form-input"
                      aria-label={`${dia.label}: apertura`}
                      value={d.horaInicio}
                      disabled={!d.abierto}
                      onChange={(e) => setDiaGeneral(i, { horaInicio: e.target.value })}
                    />
                    <span aria-hidden="true">–</span>
                    <input
                      type="time"
                      className="form-input"
                      aria-label={`${dia.label}: cierre`}
                      value={d.horaFin}
                      disabled={!d.abierto}
                      onChange={(e) => setDiaGeneral(i, { horaFin: e.target.value })}
                    />
                  </div>
                </div>
              );
            })}
          </div>
          <div className="admin-section-actions">
            <Button variant="primary" loading={savingGeneral} onClick={guardarGeneral}>
              Guardar horario
            </Button>
          </div>
        </div>
      </section>

      {/* Horario por trabajador */}
      <section className="card admin-section" aria-labelledby="horario-trab-title">
        <div className="card-header">
          <h3 id="horario-trab-title">Horario por trabajador</h3>
        </div>
        <div className="card-body">
          <Select
            id="horario-trabajador"
            label="Trabajador"
            value={trabajadorId}
            onChange={(e) => seleccionarTrabajador(e.target.value)}
          >
            <option value="">Selecciona un trabajador</option>
            {trabajadores.map((t) => (
              <option key={t.id} value={t.id}>
                {t.nombre}
              </option>
            ))}
          </Select>

          {loadingTrab && <Spinner size="md" />}

          {horarioTrab && (
            <>
              <p className="hint-text">
                "Hereda" usa el horario de la barbería. Las horas propias deben caber dentro de él.
              </p>
              <div className="horario-list">
                {horarioTrab.map((d, i) => {
                  const dia = DIAS_SEMANA[i];
                  const g = general[i];
                  const heredaTexto = g.abierto ? `Hereda (${g.horaInicio}–${g.horaFin})` : 'Hereda (cerrado)';
                  return (
                    <div key={d.diaSemana} className={`horario-row ${d.modo === LIBRE ? 'is-closed' : ''}`}>
                      <span className="horario-dia">{dia.label}</span>
                      <select
                        className="form-select horario-modo"
                        aria-label={`${dia.label}: modo`}
                        value={d.modo}
                        onChange={(e) => setDiaTrab(i, { modo: e.target.value })}
                      >
                        <option value={HEREDA}>{heredaTexto}</option>
                        <option value={LIBRE}>Día libre</option>
                        <option value={PROPIO} disabled={!g.abierto}>Horas propias</option>
                      </select>
                      {d.modo === PROPIO && (
                        <div className="horario-horas">
                          <input
                            type="time"
                            className="form-input"
                            aria-label={`${dia.label}: inicio`}
                            value={d.horaInicio}
                            min={g.horaInicio}
                            max={g.horaFin}
                            onChange={(e) => setDiaTrab(i, { horaInicio: e.target.value })}
                          />
                          <span aria-hidden="true">–</span>
                          <input
                            type="time"
                            className="form-input"
                            aria-label={`${dia.label}: fin`}
                            value={d.horaFin}
                            min={g.horaInicio}
                            max={g.horaFin}
                            onChange={(e) => setDiaTrab(i, { horaFin: e.target.value })}
                          />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              <div className="admin-section-actions">
                <Button variant="primary" loading={savingTrab} onClick={guardarTrabajador}>
                  Guardar horario del trabajador
                </Button>
              </div>
            </>
          )}
        </div>
      </section>

      {/* Avisos del último guardado (p. ej. overrides recortados por el general) */}
      {avisos.length > 0 && (
        <div className="error-state admin-section" role="status">
          <ul className="notice-list">
            {avisos.map((a, i) => <li key={i}>{a}</li>)}
          </ul>
        </div>
      )}

      {/* Conflictos */}
      <section className="card admin-section" aria-labelledby="conflictos-title">
        <div className="card-header admin-card-header">
          <h3 id="conflictos-title">Citas en conflicto (próximos 60 días)</h3>
          <Button size="sm" variant="secondary" onClick={loadConflictos} loading={loadingConflictos}>
            <LuRefreshCw aria-hidden="true" />
            Actualizar
          </Button>
        </div>
        <div className="card-body">
          <ConflictosList
            conflictos={conflictos}
            onCancelada={(reservaId) => {
              setConflictos((prev) => prev.filter((c) => c.reservaId !== reservaId));
              toast.success('Cita cancelada y cliente avisado');
            }}
          />
        </div>
      </section>
    </div>
  );
}
