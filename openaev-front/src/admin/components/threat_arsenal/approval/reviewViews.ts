import { type QueueItem } from './useApprovalQueue';

/**
 * The side filters of the review tab of the Threat Arsenal (To review for approvers, My changes for
 * authors): the Active tab's (domains, platform, status, approval) plus what only a review needs: new
 * action or new version, where it comes from, who submitted it. A built-in tab: its filters change
 * while looking and are never saved.
 */
export type ReviewMode = 'to-review' | 'my-changes';
export type FacetKey = 'domain' | 'platform' | 'status' | 'approval' | 'kind' | 'source' | 'author';

export interface ReviewFilters {
  search: string;
  /** Ticked values per side filter; a filter with nothing ticked keeps everything. */
  facets: Partial<Record<FacetKey, string[]>>;
}

export interface ReviewContext {
  me?: string;
  status: (item: QueueItem) => string | undefined;
  fromCollector: (item: QueueItem) => boolean;
  isLaterVersion: (item: QueueItem) => boolean;
  author: (item: QueueItem) => string;
  domain: (item: QueueItem) => string;
}

/** What the review tab lists when it opens: what waits for a decision. */
export const defaultFilters = (): ReviewFilters => ({
  search: '',
  facets: { approval: ['PENDING'] },
});

const mine = (item: QueueItem, context: ReviewContext) => !!context.me && item.full?.action_last_modified_by === context.me;

/** Before any filter: approvers review what others submitted, authors follow their own changes. */
export const inScope = (item: QueueItem, mode: ReviewMode, context: ReviewContext) => (mode === 'to-review' ? !mine(item, context) : mine(item, context));

/** The values of an item for one side filter (an action can run on several platforms). */
export const facetValues = (item: QueueItem, key: FacetKey, context: ReviewContext): string[] => {
  const payload = item.action.action_payload;
  switch (key) {
    case 'domain': return [context.domain(item)];
    case 'platform': return (item.action.action_platforms ?? []) as string[];
    case 'status': {
      const status = payload?.payload_revoked ? 'REVOKED' : payload?.payload_status;
      return status ? [status] : [];
    }
    case 'approval': {
      const status = context.status(item);
      return status ? [status] : [];
    }
    case 'kind': return [context.isLaterVersion(item) ? 'version' : 'new'];
    case 'source': return [context.fromCollector(item) ? 'collectors' : 'people'];
    default: return [context.author(item)];
  }
};

const passes = (item: QueueItem, filters: ReviewFilters, context: ReviewContext, except?: FacetKey) => (Object.entries(filters.facets) as [FacetKey, string[]][])
  .every(([key, ticked]) => key === except || ticked.length === 0 || facetValues(item, key, context).some(value => ticked.includes(value)));

const searched = (filters: ReviewFilters, name: string) => !filters.search || name.toLowerCase().includes(filters.search.toLowerCase());

export const matches = (item: QueueItem, filters: ReviewFilters, context: ReviewContext, name: string) => passes(item, filters, context) && searched(filters, name);

/** Counts of one side filter: what the other filters and the search keep (usual faceting). */
export const facetCounts = (items: QueueItem[], key: FacetKey, filters: ReviewFilters, context: ReviewContext, name: (item: QueueItem) => string) => {
  const counts: Record<string, number> = {};
  items
    .filter(item => passes(item, filters, context, key) && searched(filters, name(item)))
    .forEach(item => facetValues(item, key, context).forEach((value) => {
      counts[value] = (counts[value] ?? 0) + 1;
    }));
  return counts;
};
