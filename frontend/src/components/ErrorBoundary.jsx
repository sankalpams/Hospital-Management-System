import { Component } from 'react';
import { RefreshCw, TriangleAlert } from 'lucide-react';
import { Button } from './ui/Button';

/** Last-resort UI for unexpected render errors: never leave the user on a blank screen. */
export class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { failed: false };
  }

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error, info) {
    // Logged to the browser console only; no patient data is sent anywhere.
    console.error('Unhandled UI error', error, info?.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div role="alert" className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background px-4 text-center">
        <span className="flex h-12 w-12 items-center justify-center rounded-full bg-danger-soft text-danger">
          <TriangleAlert className="h-6 w-6" aria-hidden="true" />
        </span>
        <h1 className="text-xl">Something went wrong on this screen</h1>
        <p className="max-w-md text-sm text-foreground-muted">
          Your data is safe: changes are only saved when the server confirms them. Reload the page to continue.
        </p>
        <Button icon={RefreshCw} onClick={() => window.location.reload()}>Reload</Button>
      </div>
    );
  }
}
