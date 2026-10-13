/**
 * PageHeader with title, breadcrumb and actions.
 * @param {{title: string, breadcrumb?: Array<{label: string, href?: string}>, actions?: React.ReactNode}} props
 */
export default function PageHeader({ title, breadcrumb, actions }) {
  return (
    <div className="page-header">
      <div>
        {breadcrumb && breadcrumb.length > 0 && (
          <nav className="breadcrumb" aria-label="Breadcrumb">
            {breadcrumb.map((item, i) => (
              <span key={i}>
                {i > 0 && ' / '}
                {item.href ? <a href={item.href}>{item.label}</a> : item.label}
              </span>
            ))}
          </nav>
        )}
        <h1>{title}</h1>
      </div>
      {actions && <div className="page-header-actions">{actions}</div>}
    </div>
  );
}
