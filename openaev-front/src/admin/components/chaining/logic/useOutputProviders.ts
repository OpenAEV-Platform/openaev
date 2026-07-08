import { createContext, useContext } from 'react';

import type { OutputProviderEntry } from './logic-flow-helpers';

export type OutputProviderInfo = OutputProviderEntry;

export type OutputProvidersMap = Record<string, OutputProviderInfo[]>;

export interface OutputProvidersContextValue {
  providers: OutputProvidersMap;
  setProviders: (map: OutputProvidersMap) => void;
}

export const OutputProvidersContext = createContext<OutputProvidersContextValue>({
  providers: {},
  setProviders: () => {},
});

export const useOutputProviders = () => useContext(OutputProvidersContext);
