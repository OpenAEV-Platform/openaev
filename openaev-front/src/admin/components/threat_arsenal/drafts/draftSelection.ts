import { type PayloadDraftOutput, type PayloadVersionOutput } from '../../../../utils/api-types';

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
