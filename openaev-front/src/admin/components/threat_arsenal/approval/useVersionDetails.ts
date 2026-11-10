import { useEffect, useState } from 'react';

import { fetchThreatArsenalAction, fetchThreatArsenalActionApprovals } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadApprovalOutput, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import { type PayloadExecutableContent, toExecutableContent } from './payloadVersionDiff';

export interface VersionDetails {
  content?: PayloadExecutableContent;
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
 * Loads what a version runs and who submitted it. Each version is its own action, so its content
 * comes from that action and its author from the first entry of its approval history.
 */
const useVersionDetails = (actionId?: string, withHistory = true): VersionDetails => {
  const [details, setDetails] = useState<VersionDetails>(EMPTY);

  useEffect(() => {
    if (!actionId) {
      setDetails(EMPTY);
      return undefined;
    }
    let cancelled = false;
    Promise.all([
      fetchThreatArsenalAction(actionId).then(response => response.data as ThreatArsenalActionFullOutput).catch(() => undefined),
      withHistory
        ? fetchThreatArsenalActionApprovals(actionId).then(response => response.data as PayloadApprovalOutput[]).catch(() => [])
        : Promise.resolve([] as PayloadApprovalOutput[]),
    ]).then(([action, history]) => {
      if (cancelled) return;
      const sorted = [...history].sort((a, b) => b.approval_created_at.localeCompare(a.approval_created_at));
      const creation = sorted[sorted.length - 1];
      setDetails({
        content: action ? toExecutableContent(action) : undefined,
        authorName: creation?.approval_origin === 'COLLECTOR' ? undefined : creation?.approval_actor_name,
        fromCollector: creation?.approval_origin === 'COLLECTOR',
        history: sorted,
      });
    });
    return () => {
      cancelled = true;
    };
  }, [actionId, withHistory]);

  return details;
};

export default useVersionDetails;
