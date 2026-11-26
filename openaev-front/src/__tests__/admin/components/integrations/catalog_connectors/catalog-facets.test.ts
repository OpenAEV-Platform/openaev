import { describe, expect, it } from 'vitest';

import {
  type CatalogFacetFilters,
  type CatalogSort,
  type ConnectorItem,
  DEPLOYMENT_BUILT_IN,
  DEPLOYMENT_EXTERNAL,
  EMPTY_FACET_FILTERS,
  filterConnectors,
  fromCatalogConnector,
  hasActiveFacetFilters,
  prettifyUseCase,
  sortConnectors,
  STATUS_COMMUNITY,
  STATUS_FILIGRAN,
} from '../../../../../admin/components/integrations/catalog_connectors/catalog-facets';
import { type CatalogConnectorOutput } from '../../../../../utils/api-types';

const connector = (overrides: Partial<ConnectorItem>): ConnectorItem => ({
  id: overrides.title ?? 'id',
  title: 'Connector',
  type: 'INJECTOR',
  useCases: [],
  verified: false,
  external: false,
  deployedCount: 0,
  ...overrides,
});

const NMAP = connector({
  title: 'Nmap',
  description: 'Port scanner',
  type: 'INJECTOR',
  useCases: ['reconnaissance'],
  verified: true,
  external: true,
  deployedCount: 2,
});
const MITRE = connector({
  title: 'Mitre Attack',
  type: 'COLLECTOR',
  useCases: ['payload_collection'],
  deployedCount: 1,
});
const CROWDSTRIKE = connector({
  title: 'CrowdStrike',
  type: 'EXECUTOR',
  verified: true,
  deployedCount: 1,
});
const ITEMS = [NMAP, MITRE, CROWDSTRIKE];

const filters = (overrides: Partial<CatalogFacetFilters>): CatalogFacetFilters => ({
  ...EMPTY_FACET_FILTERS,
  ...overrides,
});

describe('catalog-facets', () => {
  describe('filterConnectors', () => {
    it.each([
      ['no filter', filters({}), '', [NMAP, MITRE, CROWDSTRIKE]],
      ['a keyword in the title', filters({}), 'mitre', [MITRE]],
      ['a keyword in the description', filters({}), 'SCANNER', [NMAP]],
      ['a type', filters({ types: ['COLLECTOR', 'EXECUTOR'] }), '', [MITRE, CROWDSTRIKE]],
      ['a use case', filters({ useCases: ['reconnaissance'] }), '', [NMAP]],
      ['Filigran support', filters({ status: [STATUS_FILIGRAN] }), '', [NMAP, CROWDSTRIKE]],
      ['community support', filters({ status: [STATUS_COMMUNITY] }), '', [MITRE]],
      ['both supports', filters({ status: [STATUS_FILIGRAN, STATUS_COMMUNITY] }), '', [NMAP, MITRE, CROWDSTRIKE]],
      ['an external deployment', filters({ deployment: [DEPLOYMENT_EXTERNAL] }), '', [NMAP]],
      ['a built-in deployment', filters({ deployment: [DEPLOYMENT_BUILT_IN] }), '', [MITRE, CROWDSTRIKE]],
      ['two groups', filters({
        status: [STATUS_FILIGRAN],
        types: ['EXECUTOR'],
      }), '', [CROWDSTRIKE]],
    ])('given_%s_should_keepTheMatchingConnectors', (_, facetFilters, keyword, expected) => {
      // Act
      const result = filterConnectors(ITEMS, facetFilters, keyword);

      // Assert
      expect(result).toEqual(expected);
    });

    it('given_anExcludedGroup_should_ignoreItsValues', () => {
      // Arrange
      const facetFilters = filters({
        types: ['COLLECTOR'],
        status: [STATUS_FILIGRAN],
      });

      // Act
      const result = filterConnectors(ITEMS, facetFilters, '', 'types');

      // Assert
      expect(result).toEqual([NMAP, CROWDSTRIKE]);
    });
  });

  describe('sortConnectors', () => {
    it.each<[CatalogSort, ConnectorItem[]]>([
      ['name_asc', [CROWDSTRIKE, MITRE, NMAP]],
      ['name_desc', [NMAP, MITRE, CROWDSTRIKE]],
      ['deployed_asc', [CROWDSTRIKE, MITRE, NMAP]],
      ['deployed_desc', [NMAP, CROWDSTRIKE, MITRE]],
    ])('given_%s_should_orderTheConnectors', (sort, expected) => {
      // Act
      const result = sortConnectors(ITEMS, sort);

      // Assert
      expect(result).toEqual(expected);
      expect(ITEMS).toEqual([NMAP, MITRE, CROWDSTRIKE]);
    });
  });

  describe('hasActiveFacetFilters', () => {
    it.each([
      [EMPTY_FACET_FILTERS, false],
      [filters({ deployment: [DEPLOYMENT_EXTERNAL] }), true],
    ])('given_%o_should_return_%s', (facetFilters, expected) => {
      // Act + Assert
      expect(hasActiveFacetFilters(facetFilters)).toBe(expected);
    });
  });

  describe('prettifyUseCase', () => {
    it.each([
      ['SECURITY_RESPONSE', 'Security response'],
      ['open-source-threat-intel', 'Open source threat intel'],
    ])('given_%s_should_return_%s', (useCase, expected) => {
      // Act + Assert
      expect(prettifyUseCase(useCase)).toBe(expected);
    });
  });

  describe('fromCatalogConnector', () => {
    it('given_aCatalogConnector_should_buildTheCardItem', () => {
      // Arrange
      const catalogConnector = {
        catalog_connector_id: 'c1',
        catalog_connector_title: 'Nmap',
        catalog_connector_short_description: 'Short',
        catalog_connector_type: 'INJECTOR',
        catalog_connector_logo_url: 'nmap.png',
        catalog_connector_manager_supported: true,
        catalog_connector_verified: true,
        instance_deployed_count: 3,
      } as CatalogConnectorOutput;

      // Act
      const item = fromCatalogConnector(catalogConnector);

      // Assert
      expect(item).toMatchObject({
        id: 'c1',
        description: 'Short',
        useCases: [],
        verified: true,
        external: true,
        deployedCount: 3,
        logoSrc: '/api/images/catalog/connectors/logos/nmap.png',
        detailUrl: '/admin/integrations/catalog/c1',
      });
    });
  });
});
