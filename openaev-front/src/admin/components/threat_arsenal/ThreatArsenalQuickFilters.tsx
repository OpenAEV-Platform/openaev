import { CancelOutlined, PendingOutlined, VerifiedOutlined } from '@mui/icons-material';
import { Box, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useCallback, useMemo } from 'react';

import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';

const PLATFORM_FILTER_KEY = 'action_platforms';
const STATUS_FILTER_KEY = 'action_payload_status';

const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

interface StatusOption {
  id: 'VERIFIED' | 'UNVERIFIED' | 'DEPRECATED';
  label: string;
  icon: ReactElement;
  color: string;
}

interface Props {
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

const ThreatArsenalQuickFilters: FunctionComponent<Props> = ({
  searchPaginationInput,
  filterHelpers,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const filters = searchPaginationInput.filterGroup?.filters ?? [];

  const platformFilter = useMemo(
    () => filters.find((f: Filter) => f.key === PLATFORM_FILTER_KEY),
    [filters],
  );
  const statusFilter = useMemo(
    () => filters.find((f: Filter) => f.key === STATUS_FILTER_KEY),
    [filters],
  );

  const STATUSES: StatusOption[] = useMemo(() => [
    {
      id: 'VERIFIED',
      label: t('Verified'),
      icon: <VerifiedOutlined fontSize="small" />,
      color: theme.palette.success.main,
    },
    {
      id: 'UNVERIFIED',
      label: t('Unverified'),
      icon: <PendingOutlined fontSize="small" />,
      color: theme.palette.warning.main,
    },
    {
      id: 'DEPRECATED',
      label: t('Deprecated'),
      icon: <CancelOutlined fontSize="small" />,
      color: theme.palette.text.disabled,
    },
  ], [t, theme]);

  const setFilterValues = useCallback(
    (key: string, values: string[]) => {
      const existing = filters.find((f: Filter) => f.key === key);
      if (values.length === 0) {
        if (existing?.id) {
          filterHelpers.handleRemoveFilterById(existing.id);
        } else {
          filterHelpers.handleRemoveFilterByKey(key);
        }
        return;
      }

      if (existing?.id) {
        filterHelpers.handleUpdateValuesById(existing.id, values);
        return;
      }

      filterHelpers.handleAddFilterWithEmptyValue({
        id: generateFilterId(),
        key,
        operator: 'eq',
        values,
        mode: 'and',
      });
    },
    [filterHelpers, filters],
  );

  const segmentedSx = {
    '& .MuiToggleButtonGroup-grouped': {
      'paddingInline': 1.25,
      'paddingBlock': 0.5,
      'gap': 0.5,
      'fontSize': 12,
      'fontWeight': 500,
      'textTransform': 'none' as const,
      'lineHeight': 1.2,
      'borderColor': theme.palette.divider,
      'color': theme.palette.text.secondary,
      '& .MuiSvgIcon-root': {
        fontSize: 16,
        color: 'inherit',
      },
      '&.Mui-selected': {
        'backgroundColor': alpha(theme.palette.primary.main, 0.16),
        'color': theme.palette.primary.main,
        'borderColor': alpha(theme.palette.primary.main, 0.4),
        '&:hover': { backgroundColor: alpha(theme.palette.primary.main, 0.22) },
        '& .MuiSvgIcon-root': { color: theme.palette.primary.main },
      },
    },
  };

  const renderGroupLabel = (label: string) => (
    <Typography
      sx={{
        color: 'text.secondary',
        fontFamily: '"Geologica", sans-serif',
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: '0.12em',
        textTransform: 'uppercase',
        lineHeight: 1.2,
        flexShrink: 0,
        paddingRight: 0.25,
        userSelect: 'none',
      }}
    >
      {label}
    </Typography>
  );

  return (
    <Box
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: 1.5,
        rowGap: 1,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        {renderGroupLabel(t('Platform'))}
        <ToggleButtonGroup
          value={platformFilter?.values ?? []}
          size="small"
          onChange={(_event, newValues: string[]) => setFilterValues(PLATFORM_FILTER_KEY, newValues)}
          aria-label={t('Platform')}
          sx={segmentedSx}
        >
          {PLATFORMS.map(platform => (
            <ToggleButton key={platform} value={platform} aria-label={t(platform)}>
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
              }}
              >
                <PlatformIcon platform={platform} width={14} />
                <span>{t(platform)}</span>
              </Box>
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        {renderGroupLabel(t('Status'))}
        <ToggleButtonGroup
          value={statusFilter?.values ?? []}
          size="small"
          onChange={(_event, newValues: string[]) => setFilterValues(STATUS_FILTER_KEY, newValues)}
          aria-label={t('Status')}
          sx={segmentedSx}
        >
          {STATUSES.map(status => (
            <ToggleButton key={status.id} value={status.id} aria-label={status.label}>
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
              }}
              >
                {status.icon}
                <span>{status.label}</span>
              </Box>
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Box>
    </Box>
  );
};

export default ThreatArsenalQuickFilters;
