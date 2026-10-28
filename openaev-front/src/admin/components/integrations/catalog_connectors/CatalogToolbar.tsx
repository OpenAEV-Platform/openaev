import { useTheme } from '@mui/material/styles';

import MarketplaceResultsCount from '../../../../components/common/marketplace/MarketplaceResultsCount';
import MarketplaceSortSelect from '../../../../components/common/marketplace/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../../components/common/marketplace/MarketplaceViewToggle';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';
import { type CatalogSort } from './catalog-facets';

export type MarketplaceView = 'cards' | 'list';

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
  sort: CatalogSort;
  onSortChange: (sort: CatalogSort) => void;
  resultCount: number;
  searchPlaceholder?: string;
  view: MarketplaceView;
  onViewChange: (view: MarketplaceView) => void;
}

const CatalogToolbar = ({ keyword, onSearch, searchResetKey, sort, onSortChange, resultCount, searchPlaceholder, view, onViewChange }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  // The shared field + direction control, over the catalogue's local sort values.
  const sortHelpers: SortHelpers = {
    getSortBy: () => (sort.startsWith('deployed') ? 'deployed' : 'name'),
    getSortAsc: () => sort.endsWith('_asc'),
    handleDirectedSort: (field, asc) => onSortChange(`${field}_${asc ? 'asc' : 'desc'}` as CatalogSort),
    handleSort: field => onSortChange(`${field}_asc` as CatalogSort),
  };

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
      <MarketplaceSortSelect
        sortHelpers={sortHelpers}
        options={CATALOG_SORT_OPTIONS}
        descendingFields={['deployed']}
      />
      <div style={{ marginLeft: 'auto' }}>
        <MarketplaceResultsCount count={resultCount} />
      </div>
      <MarketplaceViewToggle
        value={view === 'cards' ? 'grid' : 'list'}
        onChange={next => onViewChange(next === 'grid' ? 'cards' : 'list')}
      />
    </div>
  );
};

export default CatalogToolbar;
