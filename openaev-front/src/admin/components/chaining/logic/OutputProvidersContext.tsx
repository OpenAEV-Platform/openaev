import { createContext, type FunctionComponent, type ReactNode, useContext, useState } from 'react';

import type { OutputProviderEntry } from './logic-flow-helpers';

export type OutputProviderInfo = OutputProviderEntry;

// Maps a condition_key_type (e.g. "username") to the actions that produce it
export type OutputProvidersMap = Record<string, OutputProviderInfo[]>;

interface OutputProvidersContextValue {
  providers: OutputProvidersMap;
  setProviders: (map: OutputProvidersMap) => void;
}

const OutputProvidersContext = createContext<OutputProvidersContextValue>({
  providers: {},
  setProviders: () => {
  },
});

export const useOutputProviders = () => useContext(OutputProvidersContext);

interface Props { children: ReactNode }

export const OutputProvidersProvider: FunctionComponent<Props> = ({ children }) => {
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
