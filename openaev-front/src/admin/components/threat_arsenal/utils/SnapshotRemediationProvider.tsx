import { type ReactNode, useState } from 'react';

import { type SnapshotCollectorRemediationEditionType, SnapshotRemediationContext } from './SnapshotRemediationContext';

const SnapshotRemediationProvider = ({ children }: { children: ReactNode }) => {
  const [snapshot, setSnapshot] = useState<SnapshotCollectorRemediationEditionType>();

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
