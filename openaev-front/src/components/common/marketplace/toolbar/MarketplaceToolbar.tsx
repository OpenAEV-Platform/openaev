import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import SearchFilter from '../../../SearchFilter';
import { type SortHelpers } from '../../queryable/sort/SortHelpers';
import MarketplaceResultsCount from './MarketplaceResultsCount';
import MarketplaceSortSelect, { type SortOption } from './MarketplaceSortSelect';

interface MarketplaceToolbarState {
  keyword: string;
  onSearch: (keyword?: string) => void;
  sortHelpers: SortHelpers;
  count: number;
}

interface Props extends MarketplaceToolbarState {
  // No options, no sort menu: a list sorts from its column headers.
  sortOptions?: SortOption[];
  descendingFields?: string[];
  placeholder?: string;
  // Acts on the whole catalogue, next to the results count.
  actions?: ReactNode;
}

const MarketplaceToolbar: FunctionComponent<Props> = ({ keyword, onSearch, sortHelpers, count, sortOptions = [], descendingFields, placeholder, actions }) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'wrap',
      gap: theme.spacing(1.5),
    }}
    >
      <SearchFilter variant="small" keyword={keyword} onChange={onSearch} placeholder={placeholder} />
      {sortOptions.length > 0 && <MarketplaceSortSelect sortHelpers={sortHelpers} options={sortOptions} descendingFields={descendingFields} />}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        marginLeft: 'auto',
      }}
      >
        <MarketplaceResultsCount count={count} />
        {actions}
      </div>
    </div>
  );
};

export default MarketplaceToolbar;
