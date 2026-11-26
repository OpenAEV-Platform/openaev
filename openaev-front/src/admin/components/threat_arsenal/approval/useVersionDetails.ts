import { useEffect, useState } from 'react';

import { fetchThreatArsenalActionApprovals } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadApprovalOutput } from '../../../../utils/api-types';

export interface VersionDetails {
  /** Approval history of the version, newest first. */
  history: PayloadApprovalOutput[];
}

const EMPTY: VersionDetails = { history: [] };

/**
 * Loads the approval history of a version. Who last edited it comes with the version itself
 * ({@link lastEdited}); what it changes is loaded apart ({@link useVersionChanges}).
 */
const useVersionDetails = (actionId?: string): VersionDetails => {
  const [details, setDetails] = useState<VersionDetails>(EMPTY);

  useEffect(() => {
    if (!actionId) {
      setDetails(EMPTY);
      return undefined;
    }
    let cancelled = false;
    fetchThreatArsenalActionApprovals(actionId)
      .then(response => response.data as PayloadApprovalOutput[])
      .catch(() => [] as PayloadApprovalOutput[])
      .then((history) => {
        if (cancelled) return;
        setDetails({ history: [...history].sort((a, b) => b.approval_created_at.localeCompare(a.approval_created_at)) });
      });
    return () => {
      cancelled = true;
    };
  }, [actionId]);

  return details;
};

export default useVersionDetails;
