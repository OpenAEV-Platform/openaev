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

const DISPLAYS: Record<'PENDING' | 'REJECTED' | 'SUPERSEDED', PayloadApprovalDisplay> = {
  PENDING: {
    status: 'PAYLOAD_PENDING_APPROVAL',
    shortLabel: 'Pending approval',
    label: 'Pending approval',
    tooltip: 'The payload version of this inject is not approved: launching it is blocked until a user with "Approve content" approves it.',
  },
  SUPERSEDED: {
    status: 'PAYLOAD_REJECTED',
    shortLabel: 'Superseded',
    label: 'Payload version superseded',
    tooltip: 'The payload version of this inject was replaced by a newer version before being approved: launching it is blocked. Move the inject to an approved version.',
  },
  REJECTED: {
    status: 'PAYLOAD_REJECTED',
    shortLabel: 'Rejected',
    label: 'Rejected',
    tooltip: 'This payload was rejected: launching this inject is blocked.',
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

/** Whether the payload version of an inject blocks its launch (not approved). */
export const isApprovalBlocking = (approvalStatus?: ApprovalStatus): boolean => payloadApprovalDisplay(approvalStatus) !== undefined;

type Translate = (message: string, values?: Record<string, string>) => string;

const BLOCKER_MESSAGES: Record<LaunchBlockerOutput['approval_status'], string> = {
  PENDING: '{name} is pending approval',
  REJECTED: '{name} is rejected',
  SUPERSEDED: '{name} was replaced by a newer version',
  APPROVED: '{name} is approved',
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
