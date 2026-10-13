import { Component } from 'react';
import Button from './ui/Button';

/**
 * Error boundary wrapping layouts to catch render errors.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    // Solo mensaje + stack: el objeto error de Axios contiene config.headers
    // con el token Authorization, que no debe llegar a la consola
    console.error(
      'ErrorBoundary caught:',
      error?.message || String(error),
      errorInfo?.componentStack
    );
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
    this.props.onReset?.();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="error-boundary" role="alert">
          <h2>Algo salió mal</h2>
          <p style={{ color: 'var(--muted)' }}>
            {this.state.error?.message || 'Error inesperado en la aplicación.'}
          </p>
          <Button variant="primary" onClick={this.handleReset}>
            Reintentar
          </Button>
        </div>
      );
    }
    return this.props.children;
  }
}
