import { Badge, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { AutoAwesomeMotionOutlined, CheckCircleOutlined, ErrorOutlined } from '@mui/icons-material';
import { Box, CircularProgress, LinearProgress, Popover, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useEffect, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type BulkOperation, seedBulkOperations, useBulkOperations } from '../../../utils/bulkOperations';

/** Spinner ring around the glyph. The offset is half the size, which is what centres it. */
const SPINNER_SIZE = 32;
const SPINNER_THICKNESS = 2;
const SPINNER_CENTRING_OFFSET = `-${SPINNER_SIZE / 2}px`;

/**
 * Permanent top bar entry for massive (bulk) operations: a badge with the number of running
 * operations and a popover listing the current user's operations (live progress bars plus the
 * recent history journaled by the backend - operations are per user, never shared). Fed by the
 * user-scoped aggregated `bulk-operation` SSE events (per-entity events are suppressed during
 * massive operations), and seeded from GET /api/bulk-operations on mount.
 */
const BulkOperationsIndicator: FunctionComponent = () => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const operations = useBulkOperations();
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);

  useEffect(() => {
    seedBulkOperations();
  }, []);

  const runningCount = operations.filter(operation => operation.bulk_operation_status === 'RUNNING').length;

  const handleOpen = (event: ReactMouseEvent<HTMLButtonElement, MouseEvent>) => {
    event.preventDefault();
    setAnchorEl(event.currentTarget);
  };

  const operationTitle = (operation: BulkOperation) => {
    // Composes the i18n key from the action and the backend entity label, e.g.
    // "Deleting scenarios": the key itself is readable English if no translation exists yet.
    const actionVerbs: Record<string, string> = {
      delete: 'Deleting',
      update: 'Updating',
      test: 'Testing',
      create: 'Creating',
      realign: 'Realigning',
    };
    const action = actionVerbs[operation.bulk_operation_action] ?? 'Processing';
    return t(`${action} ${operation.bulk_operation_entity}`);
  };

  const progressValue = (operation: BulkOperation) => (operation.bulk_operation_total === 0
    ? 100
    : Math.round((operation.bulk_operation_processed / operation.bulk_operation_total) * 100));

  const statusCaption = (operation: BulkOperation) => {
    switch (operation.bulk_operation_status) {
      case 'COMPLETED':
        return t('Completed');
      case 'FAILED':
        return t('Failed');
      default:
        return `${progressValue(operation)}%`;
    }
  };

  const statusColor = (operation: BulkOperation) => {
    switch (operation.bulk_operation_status) {
      case 'COMPLETED':
        return theme.palette.success.main;
      case 'FAILED':
        return theme.palette.error.main;
      default:
        return theme.palette.primary.main;
    }
  };

  return (
    <>
      <Tooltip>
        <TooltipTrigger asChild>
          {/* The Badge wraps the BUTTON, not the glyph: it describes its child, and the glyph slot is aria-hidden. */}
          <Badge content={runningCount} circularAnchor>
            <IconButton
              priority="tertiary"
              aria-haspopup="true"
              aria-label="bulk-operations-menu"
              onClick={handleOpen}
              active={Boolean(anchorEl)}
              // The spinner overlays the glyph, and the library's icon slot is not a positioning context.
              icon={(
                <span style={{
                  position: 'relative',
                  display: 'inline-flex',
                }}
                >
                  <AutoAwesomeMotionOutlined fontSize="medium" />
                  {runningCount > 0 && (
                    <CircularProgress
                      size={SPINNER_SIZE}
                      thickness={SPINNER_THICKNESS}
                      sx={{
                        position: 'absolute',
                        top: '50%',
                        left: '50%',
                        marginTop: SPINNER_CENTRING_OFFSET,
                        marginLeft: SPINNER_CENTRING_OFFSET,
                        color: alpha(theme.palette.primary.main, 0.5),
                      }}
                    />
                  )}
                </span>
              )}
            />
          </Badge>
        </TooltipTrigger>
        <TooltipContent>{t('Massive operations')}</TooltipContent>
      </Tooltip>
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        slotProps={{
          paper: {
            sx: {
              width: 360,
              p: 0,
            },
          },
        }}
      >
        <Box
          sx={{
            px: 2,
            py: 1.5,
            borderBottom: `1px solid ${theme.palette.divider}`,
          }}
        >
          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
            {t('Massive operations')}
          </Typography>
        </Box>
        <Box sx={{
          maxHeight: 400,
          overflowY: 'auto',
        }}
        >
          {operations.length === 0 && (
            <Box
              sx={{
                px: 2,
                py: 3,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 1,
              }}
            >
              <AutoAwesomeMotionOutlined sx={{
                fontSize: 28,
                color: theme.palette.text.secondary,
              }}
              />
              <Typography variant="body2" color="textSecondary">
                {t('No massive operation yet')}
              </Typography>
            </Box>
          )}
          {operations.map((operation) => {
            const color = statusColor(operation);
            return (
              <Box
                key={operation.bulk_operation_id}
                sx={{
                  'px': 2,
                  'py': 1.5,
                  'display': 'flex',
                  'flexDirection': 'column',
                  'gap': 0.75,
                  'borderBottom': `1px solid ${theme.palette.divider}`,
                  '&:last-child': { borderBottom: 'none' },
                }}
              >
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 1,
                }}
                >
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1,
                    minWidth: 0,
                  }}
                  >
                    {operation.bulk_operation_status === 'COMPLETED' && (
                      <CheckCircleOutlined sx={{
                        fontSize: 18,
                        color,
                      }}
                      />
                    )}
                    {operation.bulk_operation_status === 'FAILED' && (
                      <ErrorOutlined sx={{
                        fontSize: 18,
                        color,
                      }}
                      />
                    )}
                    {operation.bulk_operation_status === 'RUNNING' && (
                      <CircularProgress size={14} thickness={5} sx={{ color }} />
                    )}
                    <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
                      {operationTitle(operation)}
                    </Typography>
                  </Box>
                  <Typography
                    variant="caption"
                    sx={{
                      color,
                      fontWeight: 600,
                      flexShrink: 0,
                    }}
                  >
                    {statusCaption(operation)}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={progressValue(operation)}
                  sx={{
                    'height': 6,
                    'borderRadius': 3,
                    'backgroundColor': alpha(color, 0.15),
                    '& .MuiLinearProgress-bar': {
                      borderRadius: 3,
                      backgroundColor: color,
                      transition: 'transform 0.4s ease',
                    },
                  }}
                />
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
                >
                  <Typography variant="caption" color="textSecondary">
                    {`${operation.bulk_operation_processed} / ${operation.bulk_operation_total}`}
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    {nsdt(operation.bulk_operation_started_at)}
                  </Typography>
                </Box>
              </Box>
            );
          })}
        </Box>
      </Popover>
    </>
  );
};

export default BulkOperationsIndicator;
