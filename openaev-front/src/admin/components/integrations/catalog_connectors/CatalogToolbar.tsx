import {
  ButtonGroup,
  ButtonGroupItem,
  Select,
  SelectContent,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { GridViewOutlined, ViewListOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';
import { type CatalogSort } from './catalog-facets';

export type MarketplaceView = 'cards' | 'list';

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

  const sortOptions: {
    value: CatalogSort;
    label: string;
  }[] = [
    {
      value: 'name_asc',
      label: t('Name (A-Z)'),
    },
    {
      value: 'name_desc',
      label: t('Name (Z-A)'),
    },
    {
      value: 'deployed_desc',
      label: t('Most deployed'),
    },
  ];

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
      <Select
        value={sort}
        onValueChange={next => onSortChange(next as CatalogSort)}
      >
        <SelectLabel>{t('Sort by')}</SelectLabel>
        <SelectTrigger>
          <SelectValue placeholder={t('Sort by')} />
        </SelectTrigger>
        <SelectContent>
          {sortOptions.map(option => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <span style={{
        marginLeft: 'auto',
        padding: theme.spacing(0.5, 1.25),
        borderRadius: theme.shape.borderRadius,
        backgroundColor: alpha(theme.palette.text.primary, 0.06),
        fontSize: 13,
        fontWeight: 500,
        fontVariantNumeric: 'tabular-nums',
        color: theme.palette.text.secondary,
      }}
      >
        {(() => {
          if (resultCount === 1) return t('1 result');
          return t('{count} results', { count: resultCount });
        })()}
      </span>
      <ButtonGroup
        value={view}
        size="sm"
        onValueChange={(next) => {
          const value = next as MarketplaceView;
          if (value) onViewChange(value);
        }}
        aria-label={t('View mode')}
        style={{ backgroundColor: theme.palette.background.paper }}
      >
        <Tooltip title={t('Cards view')}>
          <ButtonGroupItem value="cards" aria-label={t('Cards view')} data-testid="marketplace-view-cards" icon={<GridViewOutlined fontSize="small" />} />
        </Tooltip>
        <Tooltip title={t('List view')}>
          <ButtonGroupItem value="list" aria-label={t('List view')} data-testid="marketplace-view-list" icon={<ViewListOutlined fontSize="small" />} />
        </Tooltip>
      </ButtonGroup>
    </div>
  );
};

export default CatalogToolbar;
