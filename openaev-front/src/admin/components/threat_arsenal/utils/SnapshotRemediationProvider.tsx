import { type ReactNode, useState } from 'react';

import { SnapshotRemediationContext, type SnapshotRemediationEditionType } from './SnapshotRemediationContext';

const SnapshotRemediationProvider = ({ children }: { children: ReactNode }) => {
  const [snapshot, setSnapshot] = useState<SnapshotRemediationEditionType>();

  return (
    <SnapshotRemediationContext.Provider
      value={{
        snapshot,
        setSnapshot,
      }}
    >
      {children}
    </SnapshotRemediationContext.Provider>
  );
};

export default SnapshotRemediationProvider;
