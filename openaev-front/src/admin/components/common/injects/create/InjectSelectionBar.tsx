import { AddOutlined, CloseOutlined } from '@mui/icons-material';
import { Box, Button, IconButton, Slide, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';

interface Props {
  count: number;
  totalElements: number;
  onClear: () => void;
  onAdd: () => void;
}

// Floating selection "basket" bar of the inject-contract picker, mirroring the
// Threat Arsenal selection bar. "Add N injects" quick-creates one inject per
// selected contract (they show as "missing content" until configured).
const InjectSelectionBar: FunctionComponent<Props> = ({
  count,
  totalElements,
  onClear,
  onAdd,
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
          borderRadius: 999,
          backgroundColor: alpha(theme.palette.background.paper, 0.92),
          border: `1px solid ${theme.palette.divider}`,
          boxShadow: `0 24px 64px -24px ${alpha('#000', 0.6)}, 0 0 0 1px ${alpha(theme.palette.primary.main, 0.12)}`,
          backdropFilter: 'blur(12px)',
          maxWidth: 'calc(100vw - 48px)',
        }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          paddingRight: 1,
          borderRight: `1px solid ${theme.palette.divider}`,
        }}
        >
          <Box
            sx={{
              minWidth: 28,
              height: 28,
              borderRadius: '50%',
              backgroundColor: theme.palette.primary.main,
              color: theme.palette.primary.contrastText,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: 12,
              fontVariantNumeric: 'tabular-nums',
              paddingInline: 0.75,
            }}
          >
            {count}
          </Box>
          <Box>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {count === 1 ? t('1 action selected') : t('{count} actions selected', { count })}
            </Typography>
            {totalElements > 0 && (
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {t('out of {total}', { total: totalElements })}
              </Typography>
            )}
          </Box>
        </Box>

        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          <Tooltip title={t('Bulk select lets you add multiple injects. They\'ll show as "missing content" until configured')}>
            <Button
              color="primary"
              variant="contained"
              size="small"
              startIcon={<AddOutlined fontSize="small" />}
              onClick={onAdd}
              sx={{
                borderRadius: 999,
                textTransform: 'none',
                fontWeight: 600,
                paddingInline: 2,
              }}
            >
              {count === 1 ? t('Add 1 inject') : t('Add {count} injects', { count })}
            </Button>
          </Tooltip>

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

export default InjectSelectionBar;
