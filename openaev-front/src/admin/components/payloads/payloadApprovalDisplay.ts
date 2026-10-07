import { type PayloadSimple } from '../../../utils/api-types';

type ApprovalStatus = PayloadSimple['payload_approval_status'];

export interface PayloadApprovalDisplay {
  /** Display-only status key (colour in statusUtils), never stored on the inject. */
  status: 'PAYLOAD_PENDING_APPROVAL' | 'PAYLOAD_REJECTED';
  label: string;
  tooltip: string;
}

const DISPLAYS: Record<'PENDING' | 'REJECTED', PayloadApprovalDisplay> = {
  PENDING: {
    status: 'PAYLOAD_PENDING_APPROVAL',
    label: 'Payload pending approval',
    tooltip: 'This payload is not approved: launching this inject is blocked until a user with "Approve content" approves it.',
  },
  REJECTED: {
    status: 'PAYLOAD_REJECTED',
    label: 'Payload rejected',
    tooltip: 'This payload was rejected: launching this inject is blocked.',
  },
};

/**
 * What to show for an inject whose payload is not approved (pending or rejected), computed on read
 * from the payload approval status; undefined for an approved payload or an inject without payload.
 * Labels are translation keys.
 */
export const payloadApprovalDisplay = (approvalStatus?: ApprovalStatus): PayloadApprovalDisplay | undefined => (
  approvalStatus === 'PENDING' || approvalStatus === 'REJECTED' ? DISPLAYS[approvalStatus] : undefined
);
