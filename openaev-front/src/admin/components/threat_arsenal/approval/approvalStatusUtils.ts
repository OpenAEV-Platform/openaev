import { type ChipSeverity } from '@filigran/design-system';

import { type PayloadApprovalOutput, type ThreatArsenalActionUsageOutput } from '../../../../utils/api-types';

export type ApprovalStatus = NonNullable<PayloadApprovalOutput['approval_status']>;
export type ApprovalOrigin = NonNullable<PayloadApprovalOutput['approval_origin']>;

export const APPROVAL_STATUSES: ApprovalStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED'];

// Same ladder as the other status chips: orange "medium" for what needs attention, green "low"
// when it can be used, red "critical" when it is blocked.
const STATUS_SEVERITIES: Record<ApprovalStatus, ChipSeverity> = {
  PENDING: 'medium',
  APPROVED: 'low',
  REJECTED: 'critical',
  SUPERSEDED: 'neutral',
};

const STATUS_LABELS: Record<ApprovalStatus, string> = {
  PENDING: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUPERSEDED: 'Superseded',
};

const ORIGIN_LABELS: Record<ApprovalOrigin, string> = {
  CREATE: 'Created',
  UPDATE: 'Content edited',
  DUPLICATE: 'Duplicated',
  IMPORT: 'Imported',
  COLLECTOR: 'Synchronized by a collector',
  CATALOG: 'Verified by Filigran',
  SYSTEM: 'Built-in',
  MIGRATION: 'Approved when payload approval was introduced',
  APPROVE: 'Approved',
  REJECT: 'Rejected',
  SUPERSEDE: 'Replaced by a newer version',
  REOPEN: 'Moved back to pending approval',
};

export const approvalStatusSeverity = (status: ApprovalStatus): ChipSeverity => STATUS_SEVERITIES[status] ?? 'neutral';

export const approvalStatusLabel = (status: ApprovalStatus): string => STATUS_LABELS[status] ?? status;

/**
 * Label of the chip that stands for a whole action (card, list row, overview): a pending status
 * says what waits, a new action (cannot be used yet) or a new version (the approved one keeps
 * running), so nobody has to open the action to know.
 */
export const actionApprovalLabel = (status: ApprovalStatus, pendingVersion: boolean): string => {
  if (status !== 'PENDING') return approvalStatusLabel(status);
  return pendingVersion ? 'New version, pending approval' : 'New action, pending approval';
};

export const approvalOriginLabel = (origin: ApprovalOrigin): string => ORIGIN_LABELS[origin] ?? origin;

export const APPROVAL_COMMENT_MAX_LENGTH = 2000;

/** Whether a payload is used by at least one atomic testing, scenario or simulation still to run. */
export const isPayloadUsed = (usage?: ThreatArsenalActionUsageOutput) => !!usage
  && (usage.usage_atomic_testings_count ?? 0) + (usage.usage_scenarios_count ?? 0) + (usage.usage_simulations_count ?? 0) > 0;

/** What "New action" and "New version" mean, said the same way wherever they appear (chips, filters). */
export const NEW_ACTION_HINT = 'New action: created for the first time. Nothing can use it until it is approved.';
export const NEW_VERSION_HINT = 'New version: a change to an action already in use. The approved version keeps running until someone decides on the change.';
