import { useFormatter } from '../../../../components/i18n';
import {
  type AvailableActionOutput,
  type AvailableIntegrationActionOutput,
  type AvailablePayloadActionOutput,
} from '../../../../utils/api-types';

interface ByKind<R> {
  PAYLOAD: (action: AvailablePayloadActionOutput) => R;
  INTEGRATION: (action: AvailableIntegrationActionOutput) => R;
}

export const byKind = <R>(handlers: ByKind<R>) => (action: AvailableActionOutput): R => (
  action.action_kind === 'PAYLOAD' ? handlers.PAYLOAD(action) : handlers.INTEGRATION(action)
);

export type ActionName = (action: Pick<AvailableActionOutput, 'action_id' | 'action_labels'>) => string;

export const useActionName = (): ActionName => {
  const { tPick } = useFormatter();
  return action => tPick(action.action_labels) || Object.values(action.action_labels)[0] || action.action_id;
};

export type FacetKey = 'domains' | 'platforms';

export interface AvailableActionFilters {
  keyword: string;
  domains: string[];
  platforms: string[];
}

export type SortField = 'name' | 'publishedAt';

export interface AvailableActionSort {
  field: SortField;
  asc: boolean;
}

export const NO_FILTERS: AvailableActionFilters = {
  keyword: '',
  domains: [],
  platforms: [],
};

const FACET_KEYS: FacetKey[] = ['domains', 'platforms'];

const facetValues: Record<FacetKey, (action: AvailableActionOutput) => string[]> = {
  domains: action => (action.action_domain ? [action.action_domain] : []),
  platforms: action => action.action_platforms,
};

const kindText = byKind<string>({
  PAYLOAD: action => action.action_payload_description ?? '',
  INTEGRATION: action => action.action_integration_title,
});

const publishedAt = byKind<string>({
  PAYLOAD: action => action.action_payload_published_at ?? '',
  INTEGRATION: () => '',
});

const matchesKeyword = (action: AvailableActionOutput, keyword: string) => [
  ...Object.values(action.action_labels),
  kindText(action),
].join(' ')
  .toLowerCase()
  .includes(keyword.toLowerCase());

// Values are ORed within a facet, facets ANDed; a facet is ignored while its own counts are computed.
const matches = (action: AvailableActionOutput, filters: AvailableActionFilters, ignored?: FacetKey) => matchesKeyword(action, filters.keyword)
  && FACET_KEYS
    .filter(key => key !== ignored && filters[key].length > 0)
    .every(key => facetValues[key](action).some(value => filters[key].includes(value)));

export const hasFilters = (filters: AvailableActionFilters) => filters.keyword !== '' || FACET_KEYS.some(key => filters[key].length > 0);

export const facetCounts = (actions: AvailableActionOutput[], filters: AvailableActionFilters, key: FacetKey) => actions
  .filter(action => matches(action, filters, key))
  .flatMap(facetValues[key])
  .reduce<Record<string, number>>((counts, value) => ({
    ...counts,
    [value]: (counts[value] ?? 0) + 1,
  }), {});

// Grouped by domain, as the sections show them, the chosen sort inside a domain; no domain last.
const byDomain = (a: AvailableActionOutput, b: AvailableActionOutput) => {
  if (a.action_domain === b.action_domain) return 0;
  if (!a.action_domain) return 1;
  if (!b.action_domain) return -1;
  return a.action_domain.localeCompare(b.action_domain);
};

const sortValues: Record<SortField, (action: AvailableActionOutput, nameOf: ActionName) => string> = {
  name: (action, nameOf) => nameOf(action),
  publishedAt,
};

const bySort = ({ field, asc }: AvailableActionSort, nameOf: ActionName) => (a: AvailableActionOutput, b: AvailableActionOutput) => sortValues[field](a, nameOf)
  .localeCompare(sortValues[field](b, nameOf)) * (asc ? 1 : -1);

export const filterActions = (actions: AvailableActionOutput[], filters: AvailableActionFilters, sort: AvailableActionSort, nameOf: ActionName) => actions
  .filter(action => matches(action, filters))
  .sort((a, b) => byDomain(a, b) || bySort(sort, nameOf)(a, b));
