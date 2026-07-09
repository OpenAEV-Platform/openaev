import { type FunctionComponent, type ReactNode, useState } from 'react';

import { OutputProvidersContext, type OutputProvidersMap } from './useOutputProviders';

interface Props { children: ReactNode }

const OutputProvidersProvider: FunctionComponent<Props> = ({ children }) => {
  const [providers, setProviders] = useState<OutputProvidersMap>({});

  return (
    <OutputProvidersContext.Provider value={{
      providers,
      setProviders,
    }}
    >
      {children}
    </OutputProvidersContext.Provider>
  );
};

export default OutputProvidersProvider;
