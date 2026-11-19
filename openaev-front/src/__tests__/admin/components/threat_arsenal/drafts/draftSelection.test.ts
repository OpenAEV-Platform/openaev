import { describe, expect, it } from 'vitest';

import { defaultSelection, editOptions, parseSelection } from '../../../../../admin/components/threat_arsenal/drafts/draftSelection';
import { type PayloadDraftOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

const version = (actionId: string, number: string, status: PayloadVersionOutput['payload_approval_status']): PayloadVersionOutput => ({
  payload_action_id: actionId,
  payload_version: number,
  payload_installed: true,
  payload_approval_status: status,
} as PayloadVersionOutput);

const draft = (id: string, sourceActionId: string, rework = false): PayloadDraftOutput => ({
  draft_id: id,
  draft_source_action_id: sourceActionId,
  draft_rework: rework,
  draft_content: {},
  draft_created_at: '2026-11-19T10:00:00Z',
  draft_updated_at: '2026-11-19T10:00:00Z',
});

// Newest first, as the API lists them.
const versions = [
  version('a3', '1.2.0', 'PENDING'),
  version('a2', '1.1.0', 'REJECTED'),
  version('a1', '1.0.0', 'APPROVED'),
];

describe('draftSelection', () => {
  it('given a selector value should read its kind and id, and refuse anything else', () => {
    // Arrange / Act / Assert
    expect(parseSelection('draft:d1')).toEqual({
      kind: 'draft',
      id: 'd1',
    });
    expect(parseSelection('new:a1')?.kind).toBe('new');
    expect(parseSelection('version:a1')).toBeNull();
    expect(parseSelection('')).toBeNull();
  });

  it('given versions should offer rejected ones to fix and the others to start a draft from', () => {
    // Arrange / Act
    const options = editOptions(versions, []);

    // Assert
    expect(options.rejected.map(v => v.payload_action_id)).toEqual(['a2']);
    expect(options.published.map(v => v.payload_action_id)).toEqual(['a3', 'a1']);
  });

  it('given a rejected version already fixed in a draft should not offer it again', () => {
    // Arrange / Act
    const options = editOptions(versions, [draft('d1', 'a2', true)]);

    // Assert
    expect(options.rejected).toEqual([]);
    expect(options.drafts.map(d => d.draft_id)).toEqual(['d1']);
  });

  it('given drafts should open the last changed one', () => {
    // Arrange / Act / Assert
    expect(defaultSelection('a1', versions, [draft('d2', 'a1'), draft('d1', 'a3')])).toBe('draft:d2');
  });

  it('given no draft should fix the rejected version on screen, or start a draft from the version on screen', () => {
    // Arrange / Act / Assert
    expect(defaultSelection('a2', versions, [])).toBe('rejected:a2');
    expect(defaultSelection('a1', versions, [])).toBe('new:a1');
  });
});
