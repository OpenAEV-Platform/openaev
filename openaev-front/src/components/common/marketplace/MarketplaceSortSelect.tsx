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
import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../i18n';
import { type SortHelpers } from '../queryable/sort/SortHelpers';

export interface SortOption {
  field: string;
  label: string;
}

interface Props {
  sortHelpers: SortHelpers;
  options: SortOption[];
  /** Fields sorted newest or highest first when selected (dates, counts). */
  descendingFields?: string[];
}

/**
 * Card-view sort control: a "Sort by" field plus an asc/desc direction
 * toggle, mirroring the sortable columns available in the list view. The field
 * is the DS Combobox with its label on the left (the DS team: Select uses the
 * Combobox, whose labelPosition puts the label beside the field).
 */
const MarketplaceSortSelect: FunctionComponent<Props> = ({ sortHelpers, options, descendingFields = [] }) => {
  const { t } = useFormatter();

  const sortBy = sortHelpers.getSortBy();
  const sortAsc = sortHelpers.getSortAsc();
  // Guard against a stale localStorage sort on a field we no longer offer.
  const value = options.some(option => option.field === sortBy) ? sortBy : '';
  const selected = options.find(option => option.field === value) ?? null;

  // Switching field applies its natural direction (A->Z for names, newest
  // first for dates); re-selecting the current field keeps the direction.
  const handleFieldChange = (field: string) => {
    const asc = field === value ? sortAsc : !descendingFields.includes(field);
    sortHelpers.handleDirectedSort(field, asc);
  };

  return (
    // The toolbar row it sits in already puts 8px between its children, and the
    // same 8px between this control's own parts.
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 0.5,
      marginLeft: 1,
      // The sort control is small and must stay whole: the wider search /
      // filter inputs are the ones that compress in a tight toolbar (#7340).
      flexShrink: 0,
    }}
    >
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
        {/* 140px fits the widest option ("Updated") and the chevron while keeping the
            single-row toolbar compact at ~1512px (#7340). */}
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
    </Box>
  );
};

export default MarketplaceSortSelect;
