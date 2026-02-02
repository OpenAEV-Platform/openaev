import { Alert, AlertTitle } from '@mui/material';
import { Component, type ErrorInfo, type FunctionComponent, type LazyExoticComponent, type ReactElement, type ReactNode } from 'react';

import { sendErrorToBackend } from '../utils/Action';
import { useFormatter } from './i18n';

interface ErrorBoundaryProps {
  display?: ReactNode;
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
    if (this.state.error) {
      /* eslint-disable i18next/no-literal-string */
      return this.props.display ?? (
        <Alert severity="error">
          <AlertTitle>Error</AlertTitle>
          An unknown error occurred. Please contact your administrator or the OpenAEV maintainers.
        </Alert>
      );
      /* eslint-enable i18next/no-literal-string */
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

type ComponentType<P = object> = FunctionComponent<P> | LazyExoticComponent<FunctionComponent<P>>;

export const errorWrapper = <P extends object = Record<string, never>>(
  WrappedComponent: ComponentType<P>,
): ((props?: P) => ReactElement) => {
  const WrappedWithErrorBoundary = (props?: P): ReactElement => (
    <ErrorBoundary display={<SimpleError />}>
      <WrappedComponent {...(props ?? {} as P)} />
    </ErrorBoundary>
  );
  WrappedWithErrorBoundary.displayName = `ErrorWrapper(${(WrappedComponent as FunctionComponent<P>).displayName || (WrappedComponent as FunctionComponent<P>).name || 'Component'})`;
  return WrappedWithErrorBoundary;
};
