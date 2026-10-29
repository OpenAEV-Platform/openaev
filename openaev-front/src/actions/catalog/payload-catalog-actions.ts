import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type CatalogItemOutput,
  type CatalogPayloadInstallOutput,
  type CatalogSettingsInput,
  type CatalogSettingsOutput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import { facetsPreview, type PreviewCatalog, searchPreview } from './catalog-preview-search';

const CATALOG_URI = '/api/catalog';

export const CATALOG_DOMAIN_FILTER = 'catalog_item_category';
export const CATALOG_PLATFORM_FILTER = 'catalog_item_platforms';
export const CATALOG_AUTHOR_FILTER = 'catalog_item_author';

// Available lists what is not installed: new versions of installed payloads install themselves (F3).
export const fetchAvailableCatalogPayloads = (): Promise<CatalogItemOutput[]> => simpleCall(`${CATALOG_URI}/payloads`)
  .then(({ data }: { data: CatalogItemOutput[] }) => data.filter(item => (item.catalog_item_installed_versions ?? []).length === 0));

// The tenant catalogue is small: it is searched and faceted in memory.
const payloadCatalog = (items: CatalogItemOutput[]): PreviewCatalog<CatalogItemOutput> => ({
  items,
  text: item => `${item.catalog_item_name ?? ''} ${item.catalog_item_description ?? ''}`,
  valuesOf: (item, key) => {
    switch (key) {
      case CATALOG_DOMAIN_FILTER: return item.catalog_item_category ? [item.catalog_item_category] : [];
      case CATALOG_PLATFORM_FILTER: return item.catalog_item_platforms ?? [];
      case CATALOG_AUTHOR_FILTER: return item.catalog_item_author ? [item.catalog_item_author] : [];
      default: return [];
    }
  },
  facetKeys: [CATALOG_DOMAIN_FILTER, CATALOG_PLATFORM_FILTER, CATALOG_AUTHOR_FILTER],
});

export const searchCatalogPayloads = (input: SearchPaginationInput) => fetchAvailableCatalogPayloads()
  .then(items => searchPreview(payloadCatalog(items), input));

export const fetchCatalogPayloadFacets = (input: SearchPaginationInput) => fetchAvailableCatalogPayloads()
  .then(items => facetsPreview(payloadCatalog(items), input));

export const fetchCatalogPayloadVersions = (externalId: string): Promise<CatalogItemOutput[]> => simpleCall(`${CATALOG_URI}/payloads/${encodeURIComponent(externalId)}/versions`)
  .then(({ data }: { data: CatalogItemOutput[] }) => data);

// The released file of a version, before install; a version released as an archive has none.
export const fetchCatalogPayloadVersionContent = (externalId: string, version: string): Promise<Record<string, unknown> | null> => simpleCall(
  `${CATALOG_URI}/payloads/${encodeURIComponent(externalId)}/versions/${encodeURIComponent(version)}/content`,
  undefined,
  false,
).then(({ data }: { data: Record<string, unknown> }) => data).catch(() => null);

export const installCatalogPayload = (externalId: string, version: string): Promise<string[]> => {
  return simplePostCall(`${CATALOG_URI}/payloads/${encodeURIComponent(externalId)}/versions/${encodeURIComponent(version)}/install`)
    .then(({ data }: { data: CatalogPayloadInstallOutput }) => data.installed);
};

export const installAllCatalogPayloads = (): Promise<string[]> => simplePostCall(`${CATALOG_URI}/payloads/install-all`)
  .then(({ data }: { data: CatalogPayloadInstallOutput }) => data.installed);

export const uninstallCatalogPayload = (payloadId: string) => simpleDelCall(`${CATALOG_URI}/installed-payloads/${payloadId}`);

export const fetchCatalogSettings = (): Promise<{ data: CatalogSettingsOutput }> => simpleCall(`${CATALOG_URI}/settings`);

export const updateCatalogSettings = (input: CatalogSettingsInput): Promise<{ data: CatalogSettingsOutput }> => {
  return simplePutCall(`${CATALOG_URI}/settings`, input);
};
