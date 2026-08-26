import {
  Select,
  SelectContent,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { ArrowDownwardOutlined, ArrowUpwardOutlined } from '@mui/icons-material';
import {
  Box,
  IconButton,
  Tooltip,
} from '@mui/material';
import { type FunctionComponent } from 'react';

import { type SortHelpers } from '../../../components/common/queryable/sort/SortHelpers';
import { useFormatter } from '../../../components/i18n';
import { THREAT_ARSENAL_SORT_OPTIONS } from './threatArsenalListConfig';

interface Props { sortHelpers: SortHelpers }

/**
 * Card-view sort control: a "Sort by" select plus an asc/desc direction
 * toggle, mirroring the sortable columns available in the list view.
 */
const ThreatArsenalSortSelect: FunctionComponent<Props> = ({ sortHelpers }) => {
  const { t } = useFormatter();

  const sortBy = sortHelpers.getSortBy();
  const sortAsc = sortHelpers.getSortAsc();
  // Guard against a stale localStorage sort on a field we no longer offer.
  const value = THREAT_ARSENAL_SORT_OPTIONS.some(option => option.field === sortBy) ? sortBy : '';

  // Switching field applies its natural direction (A->Z for names, newest
  // first for dates); re-selecting the current field keeps the direction.
  const handleFieldChange = (field: string) => {
    const asc = field === value ? sortAsc : field !== 'action_updated_at';
    sortHelpers.handleDirectedSort(field, asc);
  };

  return (
    // The toolbar row it sits in already puts 8px between its children, and the
    // same 8px between this control's own parts.
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1,
    }}
    >
      <Select
        value={value}
        onValueChange={next => handleFieldChange(next)}
      >
        <SelectLabel>{t('Sort by')}</SelectLabel>
        <SelectTrigger>
          <SelectValue placeholder={t('Sort by')} />
        </SelectTrigger>
        <SelectContent>
          {THREAT_ARSENAL_SORT_OPTIONS.map(option => (
            <SelectItem key={option.field} value={option.field}>
              {t(option.label)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Tooltip title={sortAsc ? t('Sort ascending') : t('Sort descending')}>
        <span>
          <IconButton
            size="small"
            aria-label={sortAsc ? t('Sort ascending') : t('Sort descending')}
            disabled={value === ''}
            onClick={() => sortHelpers.handleDirectedSort(value, !sortAsc)}
            sx={{ color: 'text.secondary' }}
          >
            {sortAsc ? <ArrowUpwardOutlined fontSize="small" /> : <ArrowDownwardOutlined fontSize="small" />}
          </IconButton>
        </span>
      </Tooltip>
    </Box>
  );
};

export default ThreatArsenalSortSelect;
