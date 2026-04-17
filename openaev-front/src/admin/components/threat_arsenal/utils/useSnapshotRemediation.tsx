import { useContext } from 'react';

import { SnapshotRemediationContext } from './SnapshotRemediationContext';

/* eslint-disable import/prefer-default-export */
export function useSnapshotRemediation() {
  const ctx = useContext(SnapshotRemediationContext);
  if (!ctx) {
    throw new Error('useSnapshotRemediation must be used into <SnapshotRemediationProvider>');
  }
  return ctx;
}
