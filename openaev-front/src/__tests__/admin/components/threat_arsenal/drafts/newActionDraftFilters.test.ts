import { describe, expect, it } from 'vitest';

import { newActionDraftCount, newActionDraftName, shownNewActionDrafts } from '../../../../../admin/components/threat_arsenal/drafts/newActionDraftFilters';
import { type Filter, type PayloadDraftOutput, type SearchPaginationInput } from '../../../../../utils/api-types';

const draft = (id: string, name?: string): PayloadDraftOutput => ({
  draft_id: id,
  draft_new_action: true,
  draft_content: name === undefined ? {} : { action_name: name },
  draft_created_at: '2026-11-27T10:00:00Z',
  draft_updated_at: '2026-11-27T11:00:00Z',
});

const drafts = [draft('d1', 'Dump credentials'), draft('d2', 'Scan ports'), draft('d3')];

const input = (filters: Partial<Filter>[] = [], textSearch = ''): SearchPaginationInput => ({
  page: 0,
  size: 20,
  textSearch,
  filterGroup: {
    mode: 'and',
    filters: filters.map((filter, index) => ({
      id: `f${index}`,
      mode: 'and',
      operator: 'eq',
      ...filter,
    }) as Filter),
  },
});

describe('newActionDraftFilters', () => {
  describe('shownNewActionDrafts', () => {
    it('given no filter and no search should show every draft of a new action', () => {
      // Act / Assert
      expect(shownNewActionDrafts(drafts, input()).map(d => d.draft_id)).toEqual(['d1', 'd2', 'd3']);
    });

    it('given a search should keep the drafts whose name matches', () => {
      // Act / Assert
      expect(shownNewActionDrafts(drafts, input([], 'scan')).map(d => d.draft_id)).toEqual(['d2']);
    });

    it('given the Draft value of the Status filter should keep them', () => {
      // Act / Assert
      expect(shownNewActionDrafts(drafts, input([{
        key: 'action_payload_status',
        values: ['DRAFT'],
      }]))).toHaveLength(3);
    });

    it('given another status ticked without Draft should hide them', () => {
      // Act / Assert
      expect(shownNewActionDrafts(drafts, input([{
        key: 'action_payload_status',
        values: ['VERIFIED'],
      }]))).toEqual([]);
    });

    it('given a filter a draft cannot match should hide them, a view option should not', () => {
      // Act / Assert
      expect(shownNewActionDrafts(drafts, input([{
        key: 'action_platforms',
        values: ['Linux'],
      }]))).toEqual([]);
      expect(shownNewActionDrafts(drafts, input([{
        key: 'action_all_versions',
        values: ['true'],
      }]))).toHaveLength(3);
    });
  });

  describe('newActionDraftCount', () => {
    it('given another status ticked should still count the drafts the search keeps', () => {
      // Act / Assert
      expect(newActionDraftCount(drafts, input([{
        key: 'action_payload_status',
        values: ['VERIFIED'],
      }], 'dump'))).toBe(1);
    });

    it('given a filter a draft cannot match should count none', () => {
      // Act / Assert
      expect(newActionDraftCount(drafts, input([{
        key: 'action_domains',
        values: ['endpoint'],
      }]))).toBe(0);
    });
  });

  it('given a draft without a name should give an empty name', () => {
    // Act / Assert
    expect(newActionDraftName(draft('d4'))).toBe('');
    expect(newActionDraftName(draft('d5', '  Spaced  '))).toBe('Spaced');
  });
});
