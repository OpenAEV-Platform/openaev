import { IconButton, Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, SelectValue, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ArrowDownwardOutlined, ArrowUpwardOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../i18n';
import { type SortHelpers } from '../../queryable/sort/SortHelpers';

export interface SortOption {
  field: string;
  label: string;
}

interface Props {
  sortHelpers: SortHelpers;
  options: SortOption[];
  descendingFields?: string[];
}

const MarketplaceSortSelect: FunctionComponent<Props> = ({ sortHelpers, options, descendingFields = [] }) => {
  const { t } = useFormatter();

  const sortBy = sortHelpers.getSortBy();
  const sortAsc = sortHelpers.getSortAsc();
  const value = options.some(option => option.field === sortBy) ? sortBy : '';

  const handleFieldChange = (field: string) => {
    const asc = field === value ? sortAsc : !descendingFields.includes(field);
    sortHelpers.handleDirectedSort(field, asc);
  };

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 4,
      marginLeft: 8,
      flexShrink: 0,
    }}
    >
      {/* The label beside its field, the DS Select's labelPosition (RULE-13). */}
      <Select
        value={value}
        onValueChange={next => handleFieldChange(next)}
        labelPosition="left"
      >
        <SelectLabel>{t('Sort by')}</SelectLabel>
        <SelectTrigger>
          <SelectValue placeholder={t('Sort by')} />
        </SelectTrigger>
        <SelectContent>
          {options.map(option => (
            <SelectItem key={option.field} value={option.field}>
              {t(option.label)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Tooltip>
        <TooltipTrigger asChild>
          <span>
            <span className="inline-flex">
              <IconButton
                icon={sortAsc ? <ArrowUpwardOutlined fontSize="small" /> : <ArrowDownwardOutlined fontSize="small" />}
                aria-label={sortAsc ? t('Sort ascending') : t('Sort descending')}
                disabled={value === ''}
                onClick={() => sortHelpers.handleDirectedSort(value, !sortAsc)}
                priority="tertiary"
                size="sm"
              />
            </span>
          </span>
        </TooltipTrigger>
        <TooltipContent>{sortAsc ? t('Sort ascending') : t('Sort descending')}</TooltipContent>
      </Tooltip>
    </div>
  );
};

export default MarketplaceSortSelect;
