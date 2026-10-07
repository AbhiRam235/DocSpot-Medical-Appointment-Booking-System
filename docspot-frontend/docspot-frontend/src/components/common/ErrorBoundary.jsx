import { Component } from "react";

export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    // In production, wire this to your error-reporting service instead.
    console.error("Unhandled UI error:", error, info);
  }

  handleReload = () => {
    this.setState({ hasError: false });
    window.location.href = "/login";
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center bg-paper px-4 text-center">
          <h1 className="font-display text-2xl font-medium text-ink">
            Something went wrong
          </h1>
          <p className="mt-2 max-w-sm text-sm text-ink-faint">
            An unexpected error occurred. Try reloading — if it keeps
            happening, please contact support.
          </p>
          <button
            onClick={this.handleReload}
            className="mt-6 rounded bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary-dark"
          >
            Back to sign in
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
