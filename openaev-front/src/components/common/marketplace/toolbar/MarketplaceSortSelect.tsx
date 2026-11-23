import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  IconButton,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@filigran/design-system';
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
  const selected = options.find(option => option.field === value) ?? null;

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
      {/* The DS Combobox, whose labelPosition puts "Sort by" beside the field; 140px fits the widest
          option and the chevron. */}
      <Combobox
        labelPosition="left"
        options={options}
        value={selected}
        onValueChange={(next: SortOption | SortOption[] | null) => {
          if (next && !Array.isArray(next)) handleFieldChange(next.field);
        }}
        getOptionLabel={option => t(option.label)}
        isOptionEqualToValue={(a, b) => a.field === b.field}
        clearable={false}
        openOnFocus
      >
        <ComboboxLabel>{t('Sort by')}</ComboboxLabel>
        <ComboboxField style={{ width: 140 }}>
          <ComboboxInput placeholder={t('Sort by')} />
          <ComboboxControls>
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent />
      </Combobox>
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
