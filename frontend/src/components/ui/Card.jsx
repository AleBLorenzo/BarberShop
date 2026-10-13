/**
 * Card component with Header/Body/Footer slots.
 */
export default function Card({ children, className = '', ...rest }) {
  return (
    <div className={`card ${className}`} {...rest}>
      {children}
    </div>
  );
}

export function CardHeader({ title, actions, children }) {
  return (
    <div className="card-header">
      {title ? <h3>{title}</h3> : children}
      {actions}
    </div>
  );
}

export function CardBody({ children }) {
  return <div className="card-body">{children}</div>;
}

export function CardFooter({ children }) {
  return <div className="card-footer">{children}</div>;
}
