import { createContext } from 'react';
import type { FieldValues } from 'react-hook-form';

export type SnapshotEditionRemediationType = {
  AIRules?: string;
  isLoading?: boolean;
  trackedFields?: FieldValues[];
};

// Keyed by security platform id.
export type SnapshotRemediationEditionType = Map<string, SnapshotEditionRemediationType>;

export type SnapshotRemediationContextType = {
  snapshot: SnapshotRemediationEditionType | undefined;
  setSnapshot: React.Dispatch<React.SetStateAction<SnapshotRemediationEditionType | undefined>>;
};

export const SnapshotRemediationContext
  = createContext<SnapshotRemediationContextType>({
    snapshot: undefined,
    setSnapshot: () => {},
  });
