import { type CatalogPayloadOutput } from '../../../../utils/api-types';

export type FacetKey = 'domains' | 'platforms';

export interface CatalogPayloadFilters {
  keyword: string;
  domains: string[];
  platforms: string[];
}

export type SortField = 'catalog_payload_name' | 'catalog_payload_published_at';

export interface CatalogPayloadSort {
  field: SortField;
  asc: boolean;
}

export const NO_FILTERS: CatalogPayloadFilters = {
  keyword: '',
  domains: [],
  platforms: [],
};

const FACET_KEYS: FacetKey[] = ['domains', 'platforms'];

const facetValues: Record<FacetKey, (payload: CatalogPayloadOutput) => string[]> = {
  domains: payload => (payload.catalog_payload_domain ? [payload.catalog_payload_domain] : []),
  platforms: payload => payload.catalog_payload_platforms,
};

const matchesKeyword = (payload: CatalogPayloadOutput, keyword: string) => `${payload.catalog_payload_name ?? ''} ${payload.catalog_payload_description ?? ''}`
  .toLowerCase()
  .includes(keyword.toLowerCase());

// Values are ORed within a facet, facets ANDed; a facet is ignored while its own counts are computed.
const matches = (payload: CatalogPayloadOutput, filters: CatalogPayloadFilters, ignored?: FacetKey) => matchesKeyword(payload, filters.keyword)
  && FACET_KEYS
    .filter(key => key !== ignored && filters[key].length > 0)
    .every(key => facetValues[key](payload).some(value => filters[key].includes(value)));

export const hasFilters = (filters: CatalogPayloadFilters) => filters.keyword !== '' || FACET_KEYS.some(key => filters[key].length > 0);

export const facetCounts = (payloads: CatalogPayloadOutput[], filters: CatalogPayloadFilters, key: FacetKey) => payloads
  .filter(payload => matches(payload, filters, key))
  .flatMap(facetValues[key])
  .reduce<Record<string, number>>((counts, value) => ({
    ...counts,
    [value]: (counts[value] ?? 0) + 1,
  }), {});

// Grouped by domain, as the sections show them, the chosen sort inside a domain; no domain last.
const byDomain = (a: CatalogPayloadOutput, b: CatalogPayloadOutput) => {
  if (a.catalog_payload_domain === b.catalog_payload_domain) return 0;
  if (!a.catalog_payload_domain) return 1;
  if (!b.catalog_payload_domain) return -1;
  return a.catalog_payload_domain.localeCompare(b.catalog_payload_domain);
};

const bySort = ({ field, asc }: CatalogPayloadSort) => (a: CatalogPayloadOutput, b: CatalogPayloadOutput) => (a[field] ?? '').localeCompare(b[field] ?? '') * (asc ? 1 : -1);

export const filterPayloads = (payloads: CatalogPayloadOutput[], filters: CatalogPayloadFilters, sort: CatalogPayloadSort) => payloads
  .filter(payload => matches(payload, filters))
  .sort((a, b) => byDomain(a, b) || bySort(sort)(a, b));
