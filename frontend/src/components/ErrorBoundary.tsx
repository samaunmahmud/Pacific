import { Component, type ReactNode } from 'react';

/** Shows a friendly message instead of a blank page if a render error slips through. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: unknown) {
    console.error('Unhandled UI error', error);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="page page-narrow empty">
        <h1 className="page-title">Something went wrong</h1>
        <p>Please reload the page. If it keeps happening, let us know.</p>
        <a className="submit-btn" href="/">Back to Pacific</a>
      </div>
    );
  }
}
