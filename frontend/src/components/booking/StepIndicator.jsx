import { LuCheck } from 'react-icons/lu';

const STEPS = [
  { n: 1, label: 'Servicio' },
  { n: 2, label: 'Trabajador' },
  { n: 3, label: 'Fecha y hora' },
  { n: 4, label: 'Confirmar' },
];

/**
 * StepIndicator for 4-step booking wizard.
 * Escritorio: círculo + etiqueta por paso. Móvil (≤640px): solo los círculos
 * unidos por la línea de progreso y, debajo, «Paso N de 4 · Etiqueta».
 * @param {{current: number}} props current: 1-4
 */
export default function StepIndicator({ current }) {
  const actual = STEPS.find((s) => s.n === current) ?? STEPS[0];

  return (
    <nav className="step-indicator" aria-label="Progreso de la reserva">
      <ol className="step-list">
        {STEPS.map((step, i) => {
          const state = step.n === current ? 'active' : step.n < current ? 'done' : '';
          return (
            <li
              key={step.n}
              className={`step ${state}`}
              aria-current={step.n === current ? 'step' : undefined}
            >
              <span className="step-circle" aria-hidden="true">
                {step.n < current ? <LuCheck aria-hidden="true" /> : step.n}
              </span>
              <span className="step-label">
                <span className="sr-only">
                  {`Paso ${step.n}${step.n < current ? ' (completado)' : ''}: `}
                </span>
                {step.label}
              </span>
              {i < STEPS.length - 1 && <span className="step-line" aria-hidden="true" />}
            </li>
          );
        })}
      </ol>
      <p className="step-caption" aria-hidden="true">
        Paso {current} de {STEPS.length} · {actual.label}
      </p>
    </nav>
  );
}
