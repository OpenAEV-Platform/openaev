import { type FunctionComponent, type LazyExoticComponent, type ReactElement } from 'react';

import ErrorBoundary from './ErrorBoundary';

type ComponentType<P = object> = FunctionComponent<P> | LazyExoticComponent<FunctionComponent<P>>;

const getComponentName = <P,>(component: ComponentType<P>): string => {
  const fc = component as FunctionComponent<P>;
  return fc.displayName || fc.name || 'Component';
};

// eslint-disable-next-line import/prefer-default-export
export const errorWrapper = <P extends object = Record<string, never>>(
  WrappedComponent: ComponentType<P>,
): ((props?: P) => ReactElement) => {
  const WrappedWithErrorBoundary = (props?: P): ReactElement => (
    <ErrorBoundary>
      <WrappedComponent {...((props ?? {}) as P)} />
    </ErrorBoundary>
  );
  WrappedWithErrorBoundary.displayName = `ErrorWrapper(${getComponentName(WrappedComponent)})`;
  return WrappedWithErrorBoundary;
};
