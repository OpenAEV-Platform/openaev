import { Key, OnlinePredictionOutlined, SmartButtonOutlined, TerminalOutlined } from '@mui/icons-material';
import { type ReactNode, useMemo, useState } from 'react';

import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import useMarketplaceView from '../../../../components/common/marketplace/toolbar/useMarketplaceView';
import { useFormatter } from '../../../../components/i18n';
import {
  CONNECTOR_TYPE_ORDER,
  type ConnectorItem,
  type ConnectorItemType,
  filterConnectors,
  hasActiveFacetFilters,
  sortConnectors,
} from './catalog-facets';
import CatalogActiveFilters from './CatalogActiveFilters';
import CatalogConnectorCard from './CatalogConnectorCard';
import CatalogEmptyState from './CatalogEmptyState';
import CatalogSidebar from './CatalogSidebar';
import CatalogToolbar from './CatalogToolbar';
import ConnectorList from './ConnectorList';
import useCatalogFilters from './useCatalogFilters';

interface Props {
  items: ConnectorItem[];
  /** End of each card footer: deploy button, or instance status and migrate action. */
  renderAction?: (item: ConnectorItem) => ReactNode;
  searchPlaceholder?: string;
}

const VIEW_STORAGE_KEY = 'integrations_marketplace_view';

/**
 * The shared faceted marketplace browser: sticky filter sidebar, toolbar
 * (search / sort / result count), active-filter chips and type-sectioned
 * connector cards. Used by both the Available (catalog) and Deployed tabs.
 * Filter state is persisted in the URL query string (per-tab, since each tab
 * has its own URL).
 */
const ConnectorMarketplace = ({ items, renderAction, searchPlaceholder }: Props) => {
  const { t } = useFormatter();

  const { filters, keyword, setKeyword, sort, sortHelpers, onToggleFacet, onClearFacets } = useCatalogFilters();
  // SearchFilter is uncontrolled; bumping this key remounts it to clear its value.
  const [searchResetKey, setSearchResetKey] = useState(0);

  const { viewMode, handleViewModeChange } = useMarketplaceView(VIEW_STORAGE_KEY);

  const onResetAll = () => {
    onClearFacets();
    setKeyword('');
    setSearchResetKey(prev => prev + 1);
  };

  // One section per type: the chosen sort applies inside a type.
  const filteredItems = useMemo(
    () => sortConnectors(filterConnectors(items, filters, keyword), sort)
      .sort((a, b) => CONNECTOR_TYPE_ORDER.indexOf(a.type) - CONNECTOR_TYPE_ORDER.indexOf(b.type)),
    [items, filters, keyword, sort],
  );

  const typeMeta: Record<ConnectorItemType, {
    label: string;
    icon: ReactNode;
  }> = {
    COLLECTOR: {
      label: t('Collectors'),
      icon: <OnlinePredictionOutlined fontSize="small" />,
    },
    INJECTOR: {
      label: t('Injectors'),
      icon: <SmartButtonOutlined fontSize="small" />,
    },
    EXECUTOR: {
      label: t('Executors'),
      icon: <TerminalOutlined fontSize="small" />,
    },
    SECRETS_PROVIDER: {
      label: t('Secrets Provider'),
      icon: <Key fontSize="small" />,
    },
  };

  const emptyState = (keyword !== '' || hasActiveFacetFilters(filters)) && <CatalogEmptyState onResetFilters={onResetAll} />;

  return (
    <MarketplaceBrowser
      sidebar={(
        <CatalogSidebar
          connectors={items}
          filters={filters}
          keyword={keyword}
          onToggleFacet={onToggleFacet}
          onClearAll={onClearFacets}
        />
      )}
      toolbar={(
        <CatalogToolbar
          keyword={keyword}
          onSearch={value => setKeyword(value ?? '')}
          searchResetKey={searchResetKey}
          sortHelpers={sortHelpers}
          resultCount={filteredItems.length}
          searchPlaceholder={searchPlaceholder}
          view={viewMode}
          onViewChange={handleViewModeChange}
        />
      )}
    >
      <CatalogActiveFilters
        filters={filters}
        onToggleFacet={onToggleFacet}
        onClearAll={onClearFacets}
      />
      {viewMode === 'list'
        ? (
            <ConnectorList
              items={filteredItems}
              sectionLabel={type => typeMeta[type].label}
              sectionIcon={type => typeMeta[type].icon}
              renderAction={renderAction}
              sortHelpers={sortHelpers}
              emptyState={emptyState}
            />
          )
        : (
            <MarketplaceSections
              items={filteredItems}
              sectionKey={item => item.type}
              sectionLabel={type => typeMeta[type as ConnectorItemType].label}
              sectionIcon={type => typeMeta[type as ConnectorItemType].icon}
              renderItem={item => (
                <CatalogConnectorCard
                  key={item.id}
                  connector={item}
                  action={renderAction?.(item)}
                />
              )}
              emptyState={emptyState}
            />
          )}
    </MarketplaceBrowser>
  );
};

export default ConnectorMarketplace;
