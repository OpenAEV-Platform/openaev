import { type ComponentProps, type ComponentType } from 'react';

import { ErrorBoundary, SimpleError } from './Error';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const errorWrapper = (Component: ComponentType<any>) => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const WrappedComponent = (props?: ComponentProps<any>) => (
    <ErrorBoundary display={<SimpleError />}>
      <Component {...props} />
    </ErrorBoundary>
  );
  WrappedComponent.displayName = `errorWrapper(${Component.displayName || Component.name || 'Component'})`;
  return WrappedComponent;
};

export default errorWrapper;
