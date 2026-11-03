import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactNode, useMemo, useState } from 'react';

import CardGrid from '../../../../components/common/card/CardGrid';
import {
  CONNECTOR_TYPE_ORDER,
  type ConnectorItem,
  filterConnectors,
  hasActiveFacetFilters,
  sortConnectors,
} from './catalog-facets';
import CatalogActiveFilters from './CatalogActiveFilters';
import CatalogConnectorCard from './CatalogConnectorCard';
import CatalogConnectorLine, { CatalogConnectorLinesHeader } from './CatalogConnectorLine';
import CatalogEmptyState from './CatalogEmptyState';
import CatalogSidebar from './CatalogSidebar';
import CatalogToolbar, { type MarketplaceView } from './CatalogToolbar';
import useCatalogFilters from './useCatalogFilters';

interface Props {
  items: ConnectorItem[];
  /** Right side of each card footer (deploy button, instance status...). */
  /** The view is passed on: a card's footer shares its row with the use-case chips, a line's does not. */
  renderFooterAction?: (item: ConnectorItem, view: 'card' | 'line') => ReactNode;
  /** Left side of each card footer, before the support logo (health, last seen, status). */
  renderFooterStatus?: (item: ConnectorItem) => ReactNode;
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
const ConnectorMarketplace = ({ items, renderFooterAction, renderFooterStatus, searchPlaceholder }: Props) => {
  const theme = useTheme();

  const { filters, keyword, setKeyword, sort, setSort, onToggleFacet, onClearFacets } = useCatalogFilters();
  // SearchFilter is uncontrolled; bumping this key remounts it to clear its value.
  const [searchResetKey, setSearchResetKey] = useState(0);

  // Persisted so dense fleets keep the compact lines view across navigations
  // (same behavior as the OpenCTI integrations marketplace).
  const [view, setView] = useState<MarketplaceView>(
    () => (localStorage.getItem(VIEW_STORAGE_KEY) === 'list' ? 'list' : 'cards'),
  );
  const onViewChange = (value: MarketplaceView) => {
    localStorage.setItem(VIEW_STORAGE_KEY, value);
    setView(value);
  };

  const onResetAll = () => {
    onClearFacets();
    setKeyword('');
    setSearchResetKey(prev => prev + 1);
  };

  const filteredItems = useMemo(
    () => sortConnectors(filterConnectors(items, filters, keyword), sort),
    [items, filters, keyword, sort],
  );

  // No section headers: every connector in one grid, still grouped by type
  // (collectors, injectors, executors, secrets providers), sorted within it.
  const orderedItems = useMemo(
    () => CONNECTOR_TYPE_ORDER.flatMap(type => filteredItems.filter(c => c.type === type)),
    [filteredItems],
  );

  return (
    // Below md the sidebar stacks full-width above the cards instead of
    // squeezing them against a fixed 250px column.
    <Box
      sx={{
        display: 'flex',
        flexDirection: {
          xs: 'column',
          md: 'row',
        },
        gap: 3,
        alignItems: {
          xs: 'stretch',
          md: 'flex-start',
        },
      }}
    >
      <CatalogSidebar
        connectors={items}
        filters={filters}
        keyword={keyword}
        onToggleFacet={onToggleFacet}
        onClearAll={onClearFacets}
      />
      <Box
        component="main"
        sx={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
      >
        <CatalogToolbar
          keyword={keyword}
          onSearch={value => setKeyword(value ?? '')}
          searchResetKey={searchResetKey}
          sort={sort}
          onSortChange={setSort}
          resultCount={filteredItems.length}
          searchPlaceholder={searchPlaceholder}
          view={view}
          onViewChange={onViewChange}
        />
        <CatalogActiveFilters
          filters={filters}
          onToggleFacet={onToggleFacet}
          onClearAll={onClearFacets}
        />
        {orderedItems.length === 0 && (keyword !== '' || hasActiveFacetFilters(filters)) && (
          <CatalogEmptyState onResetFilters={onResetAll} />
        )}
        {orderedItems.length > 0 && view === 'list' && (
          <Box
            sx={{
              'borderRadius': 1,
              'border': `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
              'backgroundColor': theme.palette.background.paper,
              'overflow': 'hidden',
              // Row dividers live here (not on the row's own sx) so they
              // don't depend on adjacent rows sharing an emotion class.
              '& [data-testid="connector-line"] + [data-testid="connector-line"]': { borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}` },
            }}
          >
            <CatalogConnectorLinesHeader />
            {orderedItems.map(item => (
              <CatalogConnectorLine
                key={item.id}
                connector={item}
                footerAction={renderFooterAction?.(item, 'line')}
              />
            ))}
          </Box>
        )}
        {orderedItems.length > 0 && view !== 'list' && (
          <CardGrid>
            {orderedItems.map(item => (
              <CatalogConnectorCard
                key={item.id}
                connector={item}
                footerAction={renderFooterAction?.(item, 'card')}
                footerStatus={renderFooterStatus?.(item)}
              />
            ))}
          </CardGrid>
        )}
      </Box>
    </Box>
  );
};

export default ConnectorMarketplace;
