import { type PayloadVersionOutput } from '../../../utils/api-types';

// What a version dialog does: #8731's revocation (and lifting it), or taking an approval back.
export type PayloadVersionDecision = 'revoke' | 'lift' | 'reject' | 'reopen';

// Versions a decision can take: for a revocation the live ones, for lifting the revoked ones;
// rejecting takes a pending or approved version, moving back to pending an approved or rejected one.
const payloadVersionDecisionChoices = (versions: PayloadVersionOutput[], decision: PayloadVersionDecision, customized = false) => versions
  .filter((v) => {
    if (!v.payload_id || !v.payload_installed) {
      return false;
    }
    switch (decision) {
      case 'lift':
        return v.payload_revoked_in_tenant || (customized && v.payload_revoked);
      case 'reject':
        return !v.payload_revoked && (v.payload_approval_status === 'PENDING' || v.payload_approval_status === 'APPROVED');
      case 'reopen':
        return !v.payload_revoked && (v.payload_approval_status === 'APPROVED' || v.payload_approval_status === 'REJECTED');
      default:
        return !v.payload_revoked;
    }
  });

// Versions the injects of a version can move to: approved, not revoked, newest first.
export const payloadVersionMoveTargets = (versions: PayloadVersionOutput[], fromPayloadId?: string) => versions
  .filter(v => v.payload_id && v.payload_id !== fromPayloadId && v.payload_installed
    && !v.payload_revoked && v.payload_approval_status === 'APPROVED');

export default payloadVersionDecisionChoices;
