import { Close } from '@mui/icons-material';
import { Alert, Box, Chip, IconButton, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../../components/i18n';

export interface ProducingAction {
  ref: string;
  contract: string;
  statusColor: string;
  statusLabel: string;
  subtitle: string;
}

interface Props {
  // Masked, display-ready finding value (secrets already hidden by the caller).
  value: string;
  type: string;
  endpointLabel: string;
  endpointSub?: string;
  actions: ProducingAction[];
  activeRef: string | null;
  onSelect: (ref: string) => void;
  onClose: () => void;
}

// Right-side panel describing one finding picked in the attack-path graph: what it is, on which
// endpoint it was found, and which action(s) produced it. Selecting a producing action opens the
// Result & Terminal panel next to it, so the finding, its path (highlighted on the map) and the raw
// execution result can all be inspected at once. All values are rendered as inert text (secrets are
// masked upstream); nothing here is injected as HTML.
const FindingDetailPanel = ({ value, type, endpointLabel, endpointSub, actions, activeRef, onSelect, onClose }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <Paper
      variant="outlined"
      sx={{
        width: 340,
        minWidth: 0,
        overflow: 'auto',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: 1,
        px: 2.5,
        pt: 2,
        pb: 1,
      }}
      >
        <div style={{ minWidth: 0 }}>
          <Typography variant="h6" sx={{ wordBreak: 'break-all' }} title={value}>{value}</Typography>
          <Chip size="small" variant="outlined" label={type} sx={{ mt: 0.5 }} />
        </div>
        <IconButton size="small" aria-label={t('Close')} onClick={onClose} sx={{ flexShrink: 0 }}>
          <Close />
        </IconButton>
      </Box>

      <Box sx={{
        px: 2.5,
        pb: 2,
      }}
      >
        <Typography variant="subtitle2" color="text.secondary" sx={{ mt: 1 }}>
          {t('Discovered on')}
        </Typography>
        <Typography variant="body2" noWrap title={endpointLabel}>{endpointLabel}</Typography>
        {endpointSub && (
          <Typography variant="caption" color="text.secondary" noWrap>{endpointSub}</Typography>
        )}

        <Typography variant="subtitle2" color="text.secondary" sx={{ mt: 2 }}>
          {`${t('Discovered by')} (${actions.length})`}
        </Typography>
        {actions.length === 0 && (
          <Alert severity="info" sx={{ mt: 1 }}>{t('No producing action found for this finding')}</Alert>
        )}
        {actions.map(a => (
          <Box
            key={a.ref}
            role="button"
            tabIndex={0}
            onClick={() => onSelect(a.ref)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onSelect(a.ref);
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
              'borderLeft': a.ref === activeRef ? `2px solid ${theme.palette.primary.main}` : '2px solid transparent',
              'backgroundColor': a.ref === activeRef ? 'action.selected' : undefined,
              'cursor': 'pointer',
              '&:hover': { backgroundColor: 'action.hover' },
              '&:focus-visible': {
                outline: `2px solid ${theme.palette.primary.main}`,
                outlineOffset: -2,
              },
            }}
          >
            <span
              role="img"
              aria-label={a.statusLabel}
              title={a.statusLabel}
              style={{
                flex: '0 0 auto',
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: a.statusColor,
              }}
            />
            <div style={{ minWidth: 0 }}>
              <Typography variant="body2" noWrap title={a.contract}>{a.contract}</Typography>
              <Typography variant="caption" color="text.secondary" noWrap>
                {[a.statusLabel, a.subtitle].filter(Boolean).join(' · ')}
              </Typography>
            </div>
          </Box>
        ))}
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            display: 'block',
            mt: 1.5,
          }}
        >
          {t('Select an action to open its Result & Terminal view.')}
        </Typography>
      </Box>
    </Paper>
  );
};

export default FindingDetailPanel;
