import { type LaunchBlockerOutput, type PayloadSimple } from '../../../utils/api-types';

type ApprovalStatus = PayloadSimple['payload_approval_status'];

export interface PayloadApprovalDisplay {
  /** Display-only status key (colour in statusUtils), never stored on the inject. */
  status: 'PAYLOAD_PENDING_APPROVAL' | 'PAYLOAD_REJECTED';
  /** Compact chip label (status columns, header). */
  shortLabel: string;
  /** Full label, for the tooltip. */
  label: string;
  tooltip: string;
}

const DISPLAYS: Record<'PENDING' | 'REJECTED', PayloadApprovalDisplay> = {
  PENDING: {
    status: 'PAYLOAD_PENDING_APPROVAL',
    shortLabel: 'Pending',
    label: 'Payload pending approval',
    tooltip: 'This payload is not approved: launching this inject is blocked until a user with "Approve content" approves it.',
  },
  REJECTED: {
    status: 'PAYLOAD_REJECTED',
    shortLabel: 'Rejected',
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

type Translate = (message: string, values?: Record<string, string>) => string;

const BLOCKER_MESSAGES: Record<LaunchBlockerOutput['approval_status'], string> = {
  PENDING: '{name} is pending approval',
  REJECTED: '{name} is rejected',
  APPROVED: '{name} changed since its approval',
};

/** Whether these blockers keep a launch from happening. */
export const isLaunchBlocked = (blockers?: LaunchBlockerOutput[]): boolean => (blockers?.length ?? 0) > 0;

/**
 * Why a launch is not possible, naming up to 3 blocking actions then "+N", e.g.
 * "Can't launch: TEST-A is pending approval". Undefined when nothing blocks the launch.
 */
export const launchBlockedLabel = (t: Translate, blockers?: LaunchBlockerOutput[]): string | undefined => {
  if (!blockers || blockers.length === 0) {
    return undefined;
  }
  const shown = blockers.slice(0, 3).map(blocker => t(BLOCKER_MESSAGES[blocker.approval_status], { name: blocker.name })).join(', ');
  const more = blockers.length > 3 ? ` +${blockers.length - 3}` : '';
  return t('Can\'t launch: {items}', { items: `${shown}${more}` });
};
