import { createContext } from 'react';
import type { FieldValues } from 'react-hook-form';

export type SnapshotEditionRemediationType = {
  AIRules?: string;
  isLoading?: boolean;
  trackedFields?: FieldValues[];
};

export type SnapshotCollectorRemediationEditionType = Map<string, SnapshotEditionRemediationType>;

export type SnapshotRemediationContextType = {
  snapshot: SnapshotCollectorRemediationEditionType | undefined;
  setSnapshot: React.Dispatch<React.SetStateAction<SnapshotCollectorRemediationEditionType | undefined>>;
};

export const SnapshotRemediationContext
  = createContext<SnapshotRemediationContextType>({
    snapshot: undefined,
    setSnapshot: () => {},
  });
