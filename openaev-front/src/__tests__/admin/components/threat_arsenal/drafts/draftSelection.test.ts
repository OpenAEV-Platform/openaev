import { describe, expect, it } from 'vitest';

import {
  defaultSelection,
  draftOptionLabel,
  editOptions,
  parseSelection,
  publishedFromDrafts,
  rejectedOptionLabel,
  shortReason,
} from '../../../../../admin/components/threat_arsenal/drafts/draftSelection';
import { type ContentHistoryOutput, type PayloadDraftOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

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

  describe('rejection reasons in the selector', () => {
    // The keys themselves, values filled in, so the label is checked as it reads.
    const t = (key: string, values?: Record<string, string>) => Object.entries(values ?? {})
      .reduce((text, [name, value]) => text.replace(`{${name}}`, value), key);

    it('given a rejected version with a reason should show it after the number', () => {
      // Arrange
      const rejected = {
        ...version('a2', '1.1.0', 'REJECTED'),
        payload_rejection_reason: 'Deletes system logs',
      };

      // Act / Assert
      expect(rejectedOptionLabel(t, rejected)).toBe('v1.1.0 · Rejected: Deletes system logs');
      expect(rejectedOptionLabel(t, version('a2', '1.1.0', 'REJECTED'))).toBe('v1.1.0 · Rejected');
    });

    it('given a rejected version edited should show it as a draft', () => {
      // Arrange
      const fix = {
        ...draft('d1', 'a2', true),
        draft_source_version: '1.1.0',
        draft_rework_reason: 'Deletes system logs',
      };

      // Act / Assert
      expect(draftOptionLabel(t, fix)).toBe('Draft from v1.1.0');
      expect(draftOptionLabel(t, {
        ...draft('d2', 'a1'),
        draft_source_version: '1.0.0',
      })).toBe('Draft from v1.0.0');
    });

    it('given a long or multi-line reason should keep its first line, shortened', () => {
      // Act
      const short = shortReason(`${'x'.repeat(80)}\nsecond line`);

      // Assert
      expect(short).toHaveLength(60);
      expect(short.endsWith('…')).toBe(true);
      expect(shortReason('Line one\nLine two')).toBe('Line one');
    });
  });

  describe('versions published from a draft', () => {
    it('given the history should map each version published from a draft to its source', () => {
      // Arrange
      const history = [
        {
          content_history_id: 'h1',
          content_history_action: 'UPDATE',
          content_history_version: '1.3.0',
          content_history_from_version: '1.0.0',
          content_history_details: { draft: true },
        },
        {
          content_history_id: 'h2',
          content_history_action: 'UPDATE',
          content_history_version: '1.2.0',
          content_history_from_version: '1.1.0',
          content_history_details: {},
        },
        {
          content_history_id: 'h3',
          content_history_action: 'DRAFT_CREATE',
          content_history_version: '1.0.0',
          content_history_details: { draft: true },
        },
      ] as ContentHistoryOutput[];

      // Act / Assert
      expect(publishedFromDrafts(history)).toEqual({ '1.3.0': '1.0.0' });
    });
  });
});
