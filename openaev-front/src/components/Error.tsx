import { Alert, AlertTitle } from '@mui/material';
import { Component, type ErrorInfo, type FunctionComponent, type ReactNode } from 'react';

import { sendErrorToBackend } from '../utils/Action';
import { useFormatter } from './i18n';

interface ErrorBoundaryProps {
  display: ReactNode;
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
  stack: ErrorInfo | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      error: null,
      stack: null,
    };
  }

  componentDidCatch(error: Error, stack: ErrorInfo): void {
    this.setState({
      error,
      stack,
    });
    // Send the error to the backend
    sendErrorToBackend(error, stack);
  }

  render(): ReactNode {
    if (this.state.stack) {
      return this.props.display;
    }
    return this.props.children;
  }
}

// eslint-disable-next-line react-refresh/only-export-components
const SimpleError: FunctionComponent = () => {
  const { t } = useFormatter();
  return (
    <Alert severity="error">
      <AlertTitle>{t('Error')}</AlertTitle>
      {t('An unknown error occurred. Please contact your administrator or the OpenAEV maintainers.')}
    </Alert>
  );
};

export const errorWrapper = <P extends object>(WrappedComponent: FunctionComponent<P>): FunctionComponent<P> => {
  const WrappedWithErrorBoundary: FunctionComponent<P> = props => (
    <ErrorBoundary display={<SimpleError />}>
      <WrappedComponent {...props} />
    </ErrorBoundary>
  );
  WrappedWithErrorBoundary.displayName = `ErrorWrapper(${WrappedComponent.displayName || WrappedComponent.name || 'Component'})`;
  return WrappedWithErrorBoundary;
};
