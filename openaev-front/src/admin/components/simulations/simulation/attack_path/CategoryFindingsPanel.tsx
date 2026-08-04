import { Close, SearchOutlined } from '@mui/icons-material';
import { Alert, Box, IconButton, Pagination, Paper, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { AttackPathFindingItemDTO } from '../../../../../utils/api-types';
import { maskFindingValue } from './attack-path-flow-helpers';

interface Props {
  label: string;
  /** Total items matching the current search (across every page). */
  count: number;
  loading: boolean;
  /** The current page of (filtered) items. */
  items: AttackPathFindingItemDTO[];
  search: string;
  onSearchChange: (value: string) => void;
  /** 0-based current page. */
  page: number;
  pageCount: number;
  onPageChange: (page: number) => void;
  /** Server truncation note ("showing first N of M") when the loaded page missed items. */
  loadedCount: number;
  totalCount: number;
  /** Friendly endpoint hostname for an endpoint key (never the raw id); undefined hides the line. */
  endpointNameFor: (key?: string) => string | undefined;
  onItemClick: (item: AttackPathFindingItemDTO) => void;
  onClose: () => void;
}

// Contextual side panel for one finding category (portscan, credentials, cve...), replacing the old
// overlay drawer: the graph stays visible and interactive while the analyst browses, searches and
// cross-focuses items. Same Paper + header language as the endpoint/finding/execution panels.
const CategoryFindingsPanel = ({
  label,
  count,
  loading,
  items,
  search,
  onSearchChange,
  page,
  pageCount,
  onPageChange,
  loadedCount,
  totalCount,
  endpointNameFor,
  onItemClick,
  onClose,
}: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <Paper
      variant="outlined"
      style={{
        flex: 1,
        minWidth: 0,
        overflow: 'auto',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <Box sx={{
        padding: theme.spacing(2, 2.5, 1.5),
        borderBottom: `1px solid ${theme.palette.divider}`,
        flexShrink: 0,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          <Typography
            variant="h5"
            noWrap
            title={label}
            sx={{
              flex: 1,
              minWidth: 0,
              margin: 0,
            }}
          >
            {`${label} (${count})`}
          </Typography>
          <IconButton size="small" aria-label={t('Close')} onClick={onClose} sx={{ flexShrink: 0 }}>
            <Close fontSize="small" />
          </IconButton>
        </Box>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
          {t('Click any item to highlight it on the attack map and focus the producing action in the feed.')}
        </Typography>
      </Box>

      <Box sx={{
        padding: theme.spacing(2, 2.5),
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
        flex: 1,
        minHeight: 0,
      }}
      >
        <TextField
          size="small"
          fullWidth
          value={search}
          onChange={e => onSearchChange(e.target.value)}
          placeholder={t('Search')}
          InputProps={{
            startAdornment: (
              <SearchOutlined
                fontSize="small"
                sx={{
                  mr: 0.5,
                  color: 'text.secondary',
                }}
              />
            ),
          }}
        />
        {loading && (
          <Box sx={{ minHeight: 120 }}>
            <Loader variant="inElement" size="sm" />
          </Box>
        )}
        {!loading && count === 0 && (
          <Alert severity="info">{t('No findings')}</Alert>
        )}
        {!loading && (
          <Box sx={{
            flex: 1,
            minHeight: 0,
          }}
          >
            {items.map((item, index) => {
              const endpointName = endpointNameFor(item.endpointKey);
              const maskedValue = maskFindingValue(item.type, item.value);
              return (
                <Box
                  key={`${item.endpointKey}-${item.value}-${index}`}
                  role="button"
                  tabIndex={0}
                  onClick={() => onItemClick(item)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      onItemClick(item);
                    }
                  }}
                  sx={{
                    'display': 'flex',
                    'alignItems': 'center',
                    'gap': 1,
                    'py': 0.75,
                    'px': 0.5,
                    'borderRadius': 1,
                    'borderBottom': `1px solid ${theme.palette.divider}`,
                    // Same left inset as the endpoint/finding panel rows (their highlight accent slot).
                    'borderLeft': '2px solid transparent',
                    'cursor': 'pointer',
                    'transition': theme.transitions.create('background-color'),
                    '&:hover': { backgroundColor: 'action.hover' },
                    '&:focus-visible': {
                      backgroundColor: 'action.hover',
                      outline: `2px solid ${theme.palette.primary.main}`,
                      outlineOffset: -2,
                    },
                  }}
                >
                  <Box sx={{
                    'display': 'flex',
                    'alignItems': 'center',
                    'flexShrink': 0,
                    '& .MuiSvgIcon-root': { fontSize: 16 },
                  }}
                  >
                    <FindingIcon findingType={item.type ?? ''} />
                  </Box>
                  <Box sx={{
                    minWidth: 0,
                    flex: 1,
                  }}
                  >
                    <Typography variant="body2" title={maskedValue} sx={{ wordBreak: 'break-all' }}>{maskedValue}</Typography>
                    {endpointName && (
                      <Typography variant="caption" color="text.secondary" noWrap title={endpointName} sx={{ display: 'block' }}>
                        {endpointName}
                      </Typography>
                    )}
                  </Box>
                </Box>
              );
            })}
          </Box>
        )}
        {/* The panel holds one server page (bounded) and searches within it: when the category has
            more, say so rather than let the pager imply it holds everything. */}
        {!loading && totalCount > loadedCount && (
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ textAlign: 'center' }}
          >
            {t('Showing the first {count} of {total}', {
              count: loadedCount,
              total: totalCount,
            })}
          </Typography>
        )}
        {!loading && pageCount > 1 && (
          <Box sx={{
            display: 'flex',
            justifyContent: 'center',
          }}
          >
            <Pagination
              count={pageCount}
              page={page + 1}
              onChange={(_, p) => onPageChange(p - 1)}
              size="small"
              color="primary"
            />
          </Box>
        )}
      </Box>
    </Paper>
  );
};

export default CategoryFindingsPanel;
