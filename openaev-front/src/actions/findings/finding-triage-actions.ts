import { simpleCall, simplePatchCall } from '../../utils/Action';
import { type FindingTriageInput } from '../../utils/api-types';

const FINDING_TRIAGE_URI = (findingId: string) => `/api/findings/${findingId}/triage`;
const FINDING_TRIAGE_HISTORY_URI = (findingId: string) => `/api/findings/${findingId}/triage/history`;

export const fetchFindingTriage = (findingId: string) => {
  return simpleCall(FINDING_TRIAGE_URI(findingId));
};

export const updateFindingTriage = (findingId: string, status: FindingTriageInput['status'], justification: string) => {
  const data: FindingTriageInput = {
    status,
    justification,
  };
  return simplePatchCall(FINDING_TRIAGE_URI(findingId), data);
};

// Gated on Action.TRIAGE server-side (stricter than the Action.READ used for the current
// status), so a caller who can see the finding may still get a plain 403 here. The global
// error handler already no-ops on a message-less 403 (see ErrorHandler.tsx), so the default
// notify behavior is safe to keep - the caller still handles it explicitly for the UX (see
// FindingTriageHistory.tsx) instead of showing an empty-looking history.
export const fetchFindingTriageHistory = (findingId: string) => {
  return simpleCall(FINDING_TRIAGE_HISTORY_URI(findingId));
};
