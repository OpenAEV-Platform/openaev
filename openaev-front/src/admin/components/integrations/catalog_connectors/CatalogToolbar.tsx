import { useTheme } from '@mui/material/styles';

import MarketplaceResultsCount from '../../../../components/common/marketplace/toolbar/MarketplaceResultsCount';
import MarketplaceSortSelect from '../../../../components/common/marketplace/toolbar/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../../components/common/marketplace/toolbar/MarketplaceViewToggle';
import { type ViewMode } from '../../../../components/common/marketplace/toolbar/useMarketplaceView';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';

const CATALOG_SORT_OPTIONS = [
  {
    field: 'name',
    label: 'Name',
  },
  {
    field: 'deployed',
    label: 'Deployments',
  },
];

interface Props {
  keyword: string;
  onSearch: (value?: string) => void;
  searchResetKey: number;
  sortHelpers: SortHelpers;
  resultCount: number;
  searchPlaceholder?: string;
  view: ViewMode;
  onViewChange: (view: string) => void;
}

const CatalogToolbar = ({ keyword, onSearch, searchResetKey, sortHelpers, resultCount, searchPlaceholder, view, onViewChange }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    // Wraps like OpenCTI's marketplace toolbar so the fixed-width search and
    // sort controls never overflow a narrow main column.
    <div style={{
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'wrap',
      gap: theme.spacing(1.5),
    }}
    >
      <SearchFilter
        key={searchResetKey}
        variant="small"
        onChange={onSearch}
        keyword={keyword}
        placeholder={searchPlaceholder ?? `${t('Search the catalog')}...`}
      />
      {/* The list sorts from its column headers. */}
      {view === 'grid' && (
        <MarketplaceSortSelect
          sortHelpers={sortHelpers}
          options={CATALOG_SORT_OPTIONS}
          descendingFields={['deployed']}
        />
      )}
      <div style={{ marginLeft: 'auto' }}>
        <MarketplaceResultsCount count={resultCount} />
      </div>
      <MarketplaceViewToggle
        value={view}
        onChange={onViewChange}
      />
    </div>
  );
};

export default CatalogToolbar;
