import { useTheme } from '@mui/material/styles';
import { type ComponentProps } from 'react';

import PaginationComponentV2 from '../../queryable/pagination/PaginationComponentV2';
import { CATALOG_ITEM_PUBLISHED, CATALOG_ITEM_SORT_OPTIONS } from '../filter/catalogItemSearch';
import type useMarketplaceSearch from '../filter/useMarketplaceSearch';
import MarketplaceInstallActions from '../install/MarketplaceInstallActions';
import MarketplaceSortSelect from './MarketplaceSortSelect';
import MarketplaceViewToggle from './MarketplaceViewToggle';
import type useMarketplaceView from './useMarketplaceView';

interface Props<T extends object> {
  catalog: ReturnType<typeof useMarketplaceSearch<T>>;
  view: ReturnType<typeof useMarketplaceView>;
  entityPrefix: string;
  filterNames: string[];
  install: ComponentProps<typeof MarketplaceInstallActions>;
}

// The toolbar of an Available tab: filters, the sort of the grid, the install actions and the view toggle.
const MarketplaceCatalogToolbar = <T extends object>({ catalog, view, entityPrefix, filterNames, install }: Props<T>) => {
  const theme = useTheme();
  return (
    <PaginationComponentV2
      {...catalog.pagination}
      entityPrefix={entityPrefix}
      availableFilterNames={filterNames}
      filtersEndSlot={view.viewMode === 'grid'
        ? <MarketplaceSortSelect sortHelpers={catalog.queryableHelpers.sortHelpers} options={CATALOG_ITEM_SORT_OPTIONS} descendingFields={[CATALOG_ITEM_PUBLISHED]} />
        : null}
      topBarButtons={(
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          <MarketplaceInstallActions {...install} />
          <MarketplaceViewToggle value={view.viewMode} onChange={view.handleViewModeChange} />
        </div>
      )}
    />
  );
};

export default MarketplaceCatalogToolbar;
