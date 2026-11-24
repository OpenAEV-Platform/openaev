import { type ContentHistoryOutput, type PayloadDraftOutput, type PayloadVersionOutput } from '../../../../utils/api-types';

/**
 * What the edit drawer works on: a draft, a rejected version to fix (a draft is made of it at its
 * first save), or a new draft from a published version. Kept as one string for the selector.
 */
export interface DraftSelection {
  kind: 'draft' | 'rejected' | 'new';
  /** The draft id, or the action id of the version. */
  id: string;
}

export const parseSelection = (value: string): DraftSelection | null => {
  const [kind, id] = value.split(':');
  if (!id || (kind !== 'draft' && kind !== 'rejected' && kind !== 'new')) return null;
  return {
    kind,
    id,
  };
};

const installed = (versions: PayloadVersionOutput[]) => versions.filter(version => version.payload_installed && version.payload_action_id);

const fixedIn = (version: PayloadVersionOutput, drafts: PayloadDraftOutput[]) => drafts
  .some(draft => draft.draft_rework && draft.draft_source_action_id === version.payload_action_id);

/** What the selector lists: drafts, rejected versions not being fixed yet, published versions. */
export const editOptions = (versions: PayloadVersionOutput[], drafts: PayloadDraftOutput[]) => ({
  drafts,
  rejected: installed(versions).filter(version => version.payload_approval_status === 'REJECTED' && !fixedIn(version, drafts)),
  published: installed(versions).filter(version => version.payload_approval_status !== 'REJECTED'),
});

/**
 * What "Update" opens on: the last changed draft; else the version on screen when it is rejected
 * (to fix it); else a new draft from the version on screen.
 */
export const defaultSelection = (actionId: string, versions: PayloadVersionOutput[], drafts: PayloadDraftOutput[]): string => {
  if (drafts.length > 0) return `draft:${drafts[0].draft_id}`;
  const options = editOptions(versions, drafts);
  if (options.rejected.some(version => version.payload_action_id === actionId)) return `rejected:${actionId}`;
  if (options.published.some(version => version.payload_action_id === actionId)) return `new:${actionId}`;
  const first = options.published[0] ?? options.rejected[0];
  if (!first) return `new:${actionId}`;
  return `${first.payload_approval_status === 'REJECTED' ? 'rejected' : 'new'}:${first.payload_action_id}`;
};

/** A rejection reason short enough for one line of the selector (the full reason is in the Versions tab). */
export const shortReason = (reason: string, max = 60) => {
  const line = reason.split('\n')[0].trim();
  return line.length > max ? `${line.slice(0, max - 1).trimEnd()}…` : line;
};

/**
 * The versions published from a draft, with the version each draft started from (History keeps it
 * on the "Published from a draft" entry), so the Versions tab can say "from vX".
 */
export const publishedFromDrafts = (history: ContentHistoryOutput[]): Record<string, string> => Object.fromEntries(history
  .filter(entry => entry.content_history_action === 'UPDATE'
    && (entry.content_history_details as { draft?: boolean } | undefined)?.draft
    && entry.content_history_version
    && entry.content_history_from_version)
  .map(entry => [entry.content_history_version as string, entry.content_history_from_version as string]));

type Translate = (key: string, values?: Record<string, string>) => string;

/** A draft in the selector: the version it started from, or the rejected version it fixes and why. */
export const draftOptionLabel = (t: Translate, draft: PayloadDraftOutput) => {
  const version = draft.draft_source_version ?? '';
  if (!draft.draft_rework) return t('Draft from v{version}', { version });
  return draft.draft_rework_reason
    ? t('v{version} · Rejected, being fixed: {reason}', {
        version,
        reason: shortReason(draft.draft_rework_reason),
      })
    : t('v{version} · Rejected, being fixed', { version });
};

/** A rejected version in the selector, with the reason it was rejected. */
export const rejectedOptionLabel = (t: Translate, version: PayloadVersionOutput) => {
  const number = version.payload_version ?? '';
  return version.payload_rejection_reason
    ? t('v{version} · Rejected: {reason}', {
        version: number,
        reason: shortReason(version.payload_rejection_reason),
      })
    : t('v{version} · Rejected', { version: number });
};
