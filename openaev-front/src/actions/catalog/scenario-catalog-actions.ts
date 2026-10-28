import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type CatalogInstallInput,
  type CatalogInstallOutput,
  type CatalogInstallPreviewOutput,
  type CatalogItemOutput,
  type CatalogScenarioVersionsOutput,
  type ScenarioTargetsOutput,
  type SearchPaginationInput,
} from '../../utils/api-types';
import { catalogFilterSchema, type FacetCounts, facetsPreview, type PreviewCatalog, searchPreview } from './catalog-preview-search';

const CATALOG_URI = '/api/catalog';

export const SCENARIO_SEVERITY_FILTER = 'catalog_item_severity';
export const SCENARIO_CATEGORY_FILTER = 'catalog_item_category';
export const SCENARIO_TYPE_FILTER = 'catalog_item_scenario_type';
export const SCENARIO_PLATFORM_FILTER = 'catalog_item_platforms';
export const SCENARIO_AUTHOR_FILTER = 'catalog_item_author';
export const SCENARIO_INSTALLATION_FILTER = 'catalog_item_installation';
const SCENARIO_NAME_FILTER = 'catalog_item_name';
const SCENARIO_PUBLISHED_FILTER = 'catalog_item_published_at';
const SCENARIO_INJECTS_FILTER = 'catalog_item_injects_count';
export const SCENARIO_INSTALLED = 'installed';
export const SCENARIO_NOT_INSTALLED = 'not_installed';

export const fetchCatalogScenarios = (): Promise<CatalogItemOutput[]> => simpleCall(`${CATALOG_URI}/scenarios`)
  .then(({ data }: { data: CatalogItemOutput[] }) => data);

// The tenant catalogue is small: it is searched and faceted in memory.
const scenarioCatalog = (items: CatalogItemOutput[]): PreviewCatalog<CatalogItemOutput> => ({
  items,
  text: item => `${item.catalog_item_name ?? ''} ${item.catalog_item_description ?? ''}`,
  valuesOf: (item, key) => {
    switch (key) {
      case SCENARIO_SEVERITY_FILTER: return item.catalog_item_severity ? [item.catalog_item_severity] : [];
      case SCENARIO_CATEGORY_FILTER: return item.catalog_item_category ? [item.catalog_item_category] : [];
      case SCENARIO_TYPE_FILTER: return item.catalog_item_scenario_type ? [item.catalog_item_scenario_type] : [];
      case SCENARIO_PLATFORM_FILTER: return item.catalog_item_platforms ?? [];
      case SCENARIO_AUTHOR_FILTER: return item.catalog_item_author ? [item.catalog_item_author] : [];
      case SCENARIO_INSTALLATION_FILTER: return [(item.catalog_item_installed_versions ?? []).length > 0 ? SCENARIO_INSTALLED : SCENARIO_NOT_INSTALLED];
      case SCENARIO_NAME_FILTER: return [item.catalog_item_name ?? ''];
      case SCENARIO_PUBLISHED_FILTER: return item.catalog_item_published_at ? [item.catalog_item_published_at] : [];
      case SCENARIO_INJECTS_FILTER: return item.catalog_item_injects_count != null ? [String(item.catalog_item_injects_count)] : [];
      default: return [];
    }
  },
  facetKeys: [SCENARIO_INSTALLATION_FILTER, SCENARIO_SEVERITY_FILTER, SCENARIO_CATEGORY_FILTER, SCENARIO_TYPE_FILTER, SCENARIO_PLATFORM_FILTER, SCENARIO_AUTHOR_FILTER],
});

export const scenarioCatalogFilterSchemas = (facetCounts: FacetCounts | null) => {
  const valuesOf = (key: string) => Object.keys(facetCounts?.[key] ?? {});
  return [
    catalogFilterSchema(SCENARIO_NAME_FILTER, 'string'),
    catalogFilterSchema(SCENARIO_INSTALLATION_FILTER, 'string', { values: [SCENARIO_INSTALLED, SCENARIO_NOT_INSTALLED] }),
    catalogFilterSchema(SCENARIO_SEVERITY_FILTER, 'string', { values: valuesOf(SCENARIO_SEVERITY_FILTER) }),
    catalogFilterSchema(SCENARIO_CATEGORY_FILTER, 'string', { values: valuesOf(SCENARIO_CATEGORY_FILTER) }),
    catalogFilterSchema(SCENARIO_TYPE_FILTER, 'string', { values: valuesOf(SCENARIO_TYPE_FILTER) }),
    catalogFilterSchema(SCENARIO_PLATFORM_FILTER, 'string', {
      values: ['Windows', 'Linux', 'MacOS'],
      array: true,
    }),
    catalogFilterSchema(SCENARIO_AUTHOR_FILTER, 'string', { values: valuesOf(SCENARIO_AUTHOR_FILTER) }),
    catalogFilterSchema(SCENARIO_PUBLISHED_FILTER, 'instant'),
    catalogFilterSchema(SCENARIO_INJECTS_FILTER, 'integer'),
  ];
};

// Category first: the page shows one section per category.
export const searchCatalogScenarios = (input: SearchPaginationInput) => fetchCatalogScenarios()
  .then(items => searchPreview(scenarioCatalog(items), {
    ...input,
    sorts: [{
      property: SCENARIO_CATEGORY_FILTER,
      direction: 'ASC',
    }, ...(input.sorts ?? [])],
  }));

export const fetchCatalogScenarioFacets = (input: SearchPaginationInput) => fetchCatalogScenarios()
  .then(items => facetsPreview(scenarioCatalog(items), input));

export const fetchCatalogScenarioInstallPreview = (externalId: string, version: string): Promise<{ data: CatalogInstallPreviewOutput }> => {
  return simpleCall(`${CATALOG_URI}/scenarios/${encodeURIComponent(externalId)}/versions/${encodeURIComponent(version)}/install-preview`);
};

export const installCatalogScenario = (externalId: string, version: string, input: CatalogInstallInput): Promise<{ data: CatalogInstallOutput }> => {
  return simplePostCall(`${CATALOG_URI}/scenarios/${encodeURIComponent(externalId)}/versions/${encodeURIComponent(version)}/install`, input);
};

export const uninstallCatalogScenario = (scenarioId: string) => {
  return simpleDelCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}`);
};

export const fetchScenarioTargets = (scenarioId: string): Promise<{ data: ScenarioTargetsOutput }> => {
  return simpleCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/targets`);
};

export const completeScenarioTargets = (scenarioId: string, input: CatalogInstallInput): Promise<{ data: ScenarioTargetsOutput }> => {
  return simplePutCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/targets`, input, undefined, true, false);
};

export const fetchCatalogScenarioVersions = (scenarioId: string): Promise<{ data: CatalogScenarioVersionsOutput }> => {
  return simpleCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/versions`);
};

// -- UPDATE AND ROLLBACK (ADR-018) --

export type ScenarioMergeStatus = 'TAKE_APPLIED' | 'KEEP_MINE' | 'SAME' | 'CONFLICT';
export type ScenarioMergeChoice = 'KEEP_MINE' | 'TAKE_APPLIED' | 'EDIT';
export type ScenarioElementChange = 'ADDED' | 'REMOVED' | 'REMOVED_CONFLICT' | 'STAYS_DELETED' | 'KEPT_USER';

export interface ScenarioMergeChange {
  id: string;
  element: string;
  title?: string;
  block: string;
  key?: string;
  status: ScenarioMergeStatus;
  editable: boolean;
  base?: unknown;
  applied?: unknown;
  local?: unknown;
}

export interface ScenarioUpdatePreview {
  scenario_id: string;
  current_version?: string;
  target_version?: string;
  target_revision_id?: string;
  last_revision: number;
  local_hash: string;
  changes: ScenarioMergeChange[];
  elements: {
    id: string;
    kind: string;
    title?: string;
    change: ScenarioElementChange;
  }[];
  warnings: string[];
  conflicts: string[];
  payloads_to_install: string[];
  revoked_payloads: string[];
}

export interface ScenarioUpdateInput {
  version?: string;
  revision_id?: string;
  choices?: Record<string, ScenarioMergeChoice>;
  edits?: Record<string, unknown>;
  local_hash?: string;
}

export interface ScenarioRevision {
  scenario_revision_id: string;
  scenario_revision_number: number;
  scenario_revision_reason: 'INSTALL' | 'UPDATE' | 'ROLLBACK' | 'PAYLOAD_VERSION_CHANGE';
  scenario_revision_catalog_version?: string;
  scenario_revision_author?: string;
  scenario_revision_created_at: string;
}

export const fetchScenarioRevisions = (scenarioId: string): Promise<{ data: ScenarioRevision[] }> => {
  return simpleCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/revisions`);
};

export const previewScenarioUpdate = (scenarioId: string, input: ScenarioUpdateInput): Promise<{ data: ScenarioUpdatePreview }> => {
  return simplePostCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/update-preview`, input);
};

export const applyScenarioUpdate = (scenarioId: string, input: ScenarioUpdateInput): Promise<{ data: ScenarioUpdatePreview }> => {
  return simplePostCall(`${CATALOG_URI}/installed-scenarios/${scenarioId}/update`, input);
};
