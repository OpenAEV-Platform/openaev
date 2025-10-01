import { createContext } from 'react';

import { type InjectResultOverviewOutput } from '../../../utils/api-types';

export type InjectResultOverviewOutputContextType = {
  injectResultOverviewOutput: InjectResultOverviewOutput | null;
  updateInjectResultOverviewOutput: (data: InjectResultOverviewOutput) => void;
};
export const InjectResultOverviewOutputContext = createContext<InjectResultOverviewOutputContextType>({
  injectResultOverviewOutput: null,
  updateInjectResultOverviewOutput: () => {
  },
});
