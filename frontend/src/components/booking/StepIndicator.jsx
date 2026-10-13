import { LuCheck } from 'react-icons/lu';

/**
 * StepIndicator for 4-step booking wizard.
 * @param {{current: number}} props current: 1-4
 */
export default function StepIndicator({ current }) {
  const steps = [
    { n: 1, label: 'Servicio' },
    { n: 2, label: 'Trabajador' },
    { n: 3, label: 'Fecha y hora' },
    { n: 4, label: 'Confirmar' },
  ];

  return (
    <nav className="step-indicator" aria-label={`Paso ${current} de 4`}>
      {steps.map((step, i) => {
        const state = step.n === current ? 'active' : step.n < current ? 'done' : '';
        return (
          <div key={step.n} className="step-item" style={{ display: 'contents' }}>
            <div className={`step ${state}`}>
              <span className="step-circle" aria-hidden="true">
                {step.n < current ? <LuCheck aria-hidden="true" /> : step.n}
              </span>
              <span className="step-label">{step.label}</span>
            </div>
            {i < steps.length - 1 && <span className="step-line" aria-hidden="true" />}
          </div>
        );
      })}
    </nav>
  );
}
