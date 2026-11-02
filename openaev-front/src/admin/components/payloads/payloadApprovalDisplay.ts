import { type LaunchBlockerOutput, type PayloadSimple } from '../../../utils/api-types';

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

// An approved payload whose new version waits for approval: the inject keeps running the approved
// version, so this informs and never blocks.
const PENDING_VERSION_DISPLAY: PayloadApprovalDisplay = {
  status: 'PAYLOAD_PENDING_APPROVAL',
  shortLabel: 'Pending approval',
  label: 'Pending approval',
  tooltip: 'A new version of this action is pending approval. This inject keeps running the approved version until the new one is approved.',
};

const DISPLAYS: Record<'PENDING' | 'REJECTED', PayloadApprovalDisplay> = {
  PENDING: {
    status: 'PAYLOAD_PENDING_APPROVAL',
    shortLabel: 'Pending approval',
    label: 'Pending approval',
    tooltip: 'This payload is not approved: launching this inject is blocked until a user with "Approve content" approves it.',
  },
  REJECTED: {
    status: 'PAYLOAD_REJECTED',
    shortLabel: 'Rejected',
    label: 'Rejected',
    tooltip: 'This payload was rejected: launching this inject is blocked.',
  },
};

/**
 * What to show for an inject whose payload is not approved (pending or rejected), or approved with a
 * new version pending approval, computed on read; undefined for an approved payload with nothing
 * pending or an inject without payload.
 * Labels are translation keys.
 */
export const payloadApprovalDisplay = (approvalStatus?: ApprovalStatus, pendingVersion?: boolean): PayloadApprovalDisplay | undefined => {
  if (approvalStatus === 'PENDING' || approvalStatus === 'REJECTED') {
    return DISPLAYS[approvalStatus];
  }
  return approvalStatus === 'APPROVED' && pendingVersion ? PENDING_VERSION_DISPLAY : undefined;
};

/** Whether the payload of an inject blocks its launch (never approved); a pending version does not. */
export const isApprovalBlocking = (approvalStatus?: ApprovalStatus): boolean => approvalStatus === 'PENDING' || approvalStatus === 'REJECTED';

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
