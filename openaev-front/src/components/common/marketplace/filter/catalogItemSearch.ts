import { type SortField } from '../../../../utils/api-types';
import { FACET_PLATFORMS, platformFacetIcon } from '../../facets/facetPlatforms';
import { type MarketplaceFacet } from './MarketplaceFacetSidebar';

// The search fields both Available tabs share, as the catalogue items name them.
export const CATALOG_ITEM_EXTERNAL_ID = 'catalog_item_external_id';
export const CATALOG_ITEM_NAME = 'catalog_item_name';
export const CATALOG_ITEM_DESCRIPTION = 'catalog_item_description';
export const CATALOG_ITEM_PUBLISHED = 'catalog_item_published_at';
export const CATALOG_ITEM_PLATFORMS = 'catalog_item_platforms';

export const CATALOG_ITEM_DEFAULT_SORT: SortField = {
  property: CATALOG_ITEM_PUBLISHED,
  direction: 'DESC',
};

export const CATALOG_ITEM_SORT_OPTIONS = [
  {
    field: CATALOG_ITEM_NAME,
    label: 'Name',
  },
  {
    field: CATALOG_ITEM_PUBLISHED,
    label: 'Published',
  },
];

export const CATALOG_ITEM_PLATFORM_FACET: MarketplaceFacet = {
  key: CATALOG_ITEM_PLATFORMS,
  label: 'Platform',
  values: FACET_PLATFORMS,
  icon: platformFacetIcon,
};
