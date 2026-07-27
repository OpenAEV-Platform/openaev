import { CloseOutlined, DeleteOutlined, MovieFilterOutlined } from '@mui/icons-material';
import { Box, Button, IconButton, Slide, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';

interface Props {
  count: number;
  totalElements: number;
  onClear: () => void;
  onRunTest: () => void;
  /** When provided, shows a mass-delete button in the selection bar. */
  onDelete?: () => void;
}

const ThreatArsenalSelectionBar: FunctionComponent<Props> = ({
  count,
  totalElements,
  onClear,
  onRunTest,
  onDelete,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const open = count > 0;

  return (
    <Slide direction="up" in={open} mountOnEnter unmountOnExit>
      <Box
        role="region"
        aria-label={t('Selection toolbar')}
        sx={{
          position: 'fixed',
          left: '50%',
          bottom: 24,
          transform: 'translateX(-50%)',
          zIndex: theme.zIndex.snackbar,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          paddingBlock: 1.25,
          paddingInline: 2,
          borderRadius: 1,
          backgroundColor: alpha(theme.palette.background.paper, 0.92),
          border: `1px solid ${theme.palette.divider}`,
          boxShadow: `0 24px 64px -24px ${alpha('#000', 0.6)}, 0 0 0 1px ${alpha(theme.palette.primary.main, 0.12)}`,
          backdropFilter: 'blur(12px)',
          maxWidth: 'calc(100vw - 48px)',
        }}
      >
        {/* The counts live in the text only: a separate count bubble would just
            repeat the "N actions selected out of N" line next to it. */}
        <Box sx={{
          paddingRight: 1,
          borderRight: `1px solid ${theme.palette.divider}`,
        }}
        >
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {count === 1 ? t('1 action selected') : t('{count} actions selected', { count })}
          </Typography>
          {totalElements > 0 && (
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {t('out of {total}', { total: totalElements })}
            </Typography>
          )}
        </Box>

        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          <Button
            color="primary"
            variant="contained"
            size="small"
            startIcon={<MovieFilterOutlined fontSize="small" />}
            onClick={onRunTest}
            sx={{
              borderRadius: 1,
              textTransform: 'none',
              fontWeight: 600,
              paddingInline: 2,
            }}
          >
            {t('Run a test')}
          </Button>

          {onDelete && (
            <Button
              color="error"
              variant="outlined"
              size="small"
              startIcon={<DeleteOutlined fontSize="small" />}
              onClick={onDelete}
              sx={{
                borderRadius: 1,
                textTransform: 'none',
                fontWeight: 600,
                paddingInline: 2,
              }}
            >
              {t('Delete')}
            </Button>
          )}

          <Tooltip title={t('Clear selection')}>
            <IconButton
              size="small"
              onClick={onClear}
              aria-label={t('Clear selection')}
              sx={{ color: 'text.secondary' }}
            >
              <CloseOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>
    </Slide>
  );
};

export default ThreatArsenalSelectionBar;
