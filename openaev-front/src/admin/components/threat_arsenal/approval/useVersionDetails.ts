import { useEffect, useState } from 'react';

import { fetchThreatArsenalActionApprovals } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadApprovalOutput } from '../../../../utils/api-types';

export interface VersionDetails {
  /** Who created the version: the first entry of its approval history. */
  authorName?: string;
  /** Created by a collector (no person to name). */
  fromCollector: boolean;
  /** Approval history of the version, newest first. */
  history: PayloadApprovalOutput[];
}

const EMPTY: VersionDetails = {
  fromCollector: false,
  history: [],
};

/**
 * Loads who submitted a version and its approval history: its author is the first entry of that
 * history. What the version changes is loaded apart ({@link useVersionChanges}).
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
        const sorted = [...history].sort((a, b) => b.approval_created_at.localeCompare(a.approval_created_at));
        const creation = sorted[sorted.length - 1];
        setDetails({
          authorName: creation?.approval_origin === 'COLLECTOR' ? undefined : creation?.approval_actor_name,
          fromCollector: creation?.approval_origin === 'COLLECTOR',
          history: sorted,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [actionId]);

  return details;
};

export default useVersionDetails;
