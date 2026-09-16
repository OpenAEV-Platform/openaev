import { ArrowDownwardOutlined, ArrowUpwardOutlined } from '@mui/icons-material';
import {
  Box,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
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
      {/* 110px fits the widest option ("Updated") and the "Sort by" label
          while keeping the single-row toolbar compact at ~1512px (#7340). */}
      <FormControl size="small" sx={{ minWidth: 110 }}>
        <InputLabel id="threat-arsenal-sort-by-label">{t('Sort by')}</InputLabel>
        <Select
          labelId="threat-arsenal-sort-by-label"
          label={t('Sort by')}
          value={value}
          onChange={event => handleFieldChange(event.target.value)}
        >
          {THREAT_ARSENAL_SORT_OPTIONS.map(option => (
            <MenuItem key={option.field} value={option.field}>
              {t(option.label)}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
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
