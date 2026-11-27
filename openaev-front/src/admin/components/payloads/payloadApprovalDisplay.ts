import { type PayloadSimple } from '../../../utils/api-types';

type ApprovalStatus = PayloadSimple['payload_approval_status'];

// One wording everywhere (list, drawer, inject lists, atomic testing header): the chip says
// "Pending approval" or "Rejected", like the Threat Arsenal; the tooltip explains what it means here.
export interface PayloadApprovalDisplay {
  /** Display-only status key (colour in statusUtils), never stored on the inject. */
  status: 'PAYLOAD_PENDING_APPROVAL' | 'PAYLOAD_REJECTED';
  /** Compact chip label (status columns, header). */
  shortLabel: string;
  /** Full label, for the tooltip. */
  label: string;
  tooltip: string;
}

const DISPLAYS: Record<'PENDING' | 'REJECTED' | 'SUPERSEDED', PayloadApprovalDisplay> = {
  PENDING: {
    status: 'PAYLOAD_PENDING_APPROVAL',
    shortLabel: 'Pending approval',
    label: 'Pending approval',
    tooltip: 'The payload version of this inject is not approved: the inject fails when it runs until a user with "Approve content" approves it.',
  },
  SUPERSEDED: {
    status: 'PAYLOAD_REJECTED',
    shortLabel: 'Superseded',
    label: 'Payload version superseded',
    tooltip: 'The payload version of this inject was replaced by a newer version before being approved: the inject fails when it runs. Move the inject to an approved version.',
  },
  REJECTED: {
    status: 'PAYLOAD_REJECTED',
    shortLabel: 'Rejected',
    label: 'Rejected',
    tooltip: 'This payload was rejected: this inject fails when it runs.',
  },
};

/**
 * What to show for an inject whose payload version is not approved (pending, rejected or
 * superseded), computed on read; undefined for an approved version or an inject without payload.
 * Labels are translation keys.
 */
export const payloadApprovalDisplay = (approvalStatus?: ApprovalStatus): PayloadApprovalDisplay | undefined => (
  approvalStatus === 'PENDING' || approvalStatus === 'REJECTED' || approvalStatus === 'SUPERSEDED'
    ? DISPLAYS[approvalStatus]
    : undefined);
