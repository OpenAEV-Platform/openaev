import { type ChipSeverity } from '@filigran/design-system';

import { type PayloadApprovalOutput, type ThreatArsenalActionUsageOutput } from '../../../../utils/api-types';

export type ApprovalStatus = NonNullable<PayloadApprovalOutput['approval_status']>;
export type ApprovalOrigin = NonNullable<PayloadApprovalOutput['approval_origin']>;

export const APPROVAL_STATUSES: ApprovalStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];

// Same ladder as the other status chips: orange "medium" for what needs attention, green "low"
// when it can be used, red "critical" when it is blocked.
const STATUS_SEVERITIES: Record<ApprovalStatus, ChipSeverity> = {
  PENDING: 'medium',
  APPROVED: 'low',
  REJECTED: 'critical',
};

const STATUS_LABELS: Record<ApprovalStatus, string> = {
  PENDING: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
};

const ORIGIN_LABELS: Record<ApprovalOrigin, string> = {
  CREATE: 'Created',
  UPDATE: 'Content edited',
  DUPLICATE: 'Duplicated',
  IMPORT: 'Imported',
  COLLECTOR: 'Synchronized by a collector',
  SYSTEM: 'Built-in',
  MIGRATION: 'Approved when payload approval was introduced',
  APPROVE: 'Approved',
  REJECT: 'Rejected',
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

/**
 * The status shown for an action: its latest version's. An approved action with a new version
 * waiting shows PENDING; the approved version keeps running until that version is decided.
 */
export const displayedApprovalStatus = <S extends ApprovalStatus | null | undefined>(status: S, pendingVersion?: boolean): S | 'PENDING' => (pendingVersion ? 'PENDING' : status);

export const approvalOriginLabel = (origin: ApprovalOrigin): string => ORIGIN_LABELS[origin] ?? origin;

export const APPROVAL_COMMENT_MAX_LENGTH = 2000;

/** Whether a payload is used by at least one atomic testing, scenario or simulation still to run. */
export const isPayloadUsed = (usage?: ThreatArsenalActionUsageOutput) => !!usage
  && (usage.usage_atomic_testings_count ?? 0) + (usage.usage_scenarios_count ?? 0) + (usage.usage_simulations_count ?? 0) > 0;
