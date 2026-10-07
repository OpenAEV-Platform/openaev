import { type ChipSeverity } from '@filigran/design-system';

import { type PayloadApprovalOutput } from '../../../../utils/api-types';

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

export const approvalOriginLabel = (origin: ApprovalOrigin): string => ORIGIN_LABELS[origin] ?? origin;

export const APPROVAL_COMMENT_MAX_LENGTH = 2000;
