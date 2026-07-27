import { Close } from '@mui/icons-material';
import { Alert, Button, Chip, IconButton, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../../components/i18n';
import expectationIconByType from '../../../common/ExpectationIconByType';

export interface ProducingAction {
  ref: string;
  contract: string;
  statusColor: string;
  statusLabel: string;
  subtitle: string;
}

export type ExpectationVerdict = 'success' | 'failed' | 'unknown';

export interface FindingExpectations {
  prevention?: ExpectationVerdict;
  detection?: ExpectationVerdict;
  vulnerability?: ExpectationVerdict;
}

interface Props {
  // Masked, display-ready finding value (secrets already hidden by the caller).
  value: string;
  type: string;
  endpointLabel: string;
  endpointSub?: string;
  // Prevention / detection / vulnerability verdicts for this finding (backend-provided).
  expectations?: FindingExpectations;
  actions: ProducingAction[];
  activeRef: string | null;
  onSelect: (ref: string) => void;
  onClose: () => void;
}

const EXPECTATION_ORDER: (keyof FindingExpectations)[] = ['prevention', 'detection', 'vulnerability'];

// Right-side panel describing one finding picked in the attack-path graph: its prevention/detection/
// vulnerability verdicts, what it is, on which endpoint it was found, and which action(s) produced it.
// Selecting a producing action opens the Result & Terminal panel next to it, so the finding, its path
// (highlighted on the map) and the raw execution result can all be inspected at once. All values are
// rendered as inert text (secrets are masked upstream); nothing here is injected as HTML.
const FindingDetailPanel = ({ value, type, endpointLabel, endpointSub, expectations, actions, activeRef, onSelect, onClose }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  // Colour a verdict per its expectation type: a successful prevention is green, a successful
  // detection is orange, a failed verdict is red, and an unknown one is muted.
  const verdictColor = (key: keyof FindingExpectations, verdict?: ExpectationVerdict): string => {
    if (verdict === 'success') {
      return key === 'detection' ? theme.palette.warning.main : theme.palette.success.main;
    }
    if (verdict === 'failed') {
      return theme.palette.error.main;
    }
    return theme.palette.text.disabled;
  };

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
      <div style={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: theme.spacing(1),
        padding: theme.spacing(2, 2.5, 1),
      }}
      >
        <div style={{ minWidth: 0 }}>
          {expectations && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1),
              marginBottom: theme.spacing(1),
            }}
            >
              {EXPECTATION_ORDER.map((key) => {
                const verdict = expectations[key] ?? 'unknown';
                const label = `${t(key.charAt(0).toUpperCase() + key.slice(1))}: ${t(verdict.charAt(0).toUpperCase() + verdict.slice(1))}`;
                return (
                  <span
                    key={key}
                    role="img"
                    aria-label={label}
                    title={label}
                    style={{ display: 'inline-flex' }}
                  >
                    {expectationIconByType(key, { color: verdictColor(key, verdict) })}
                  </span>
                );
              })}
            </div>
          )}
          <Typography variant="h6" sx={{ wordBreak: 'break-all' }} title={value}>{value}</Typography>
          <Chip size="small" variant="outlined" label={type} sx={{ mt: 0.5 }} />
        </div>
        <IconButton size="small" aria-label={t('Close')} onClick={onClose} sx={{ flexShrink: 0 }}>
          <Close />
        </IconButton>
      </div>

      <div style={{ padding: theme.spacing(0, 2.5, 2) }}>
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
          <div
            key={a.ref}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1),
              padding: theme.spacing(0.75, 0.5),
              borderBottom: `1px solid ${theme.palette.divider}`,
              borderLeft: a.ref === activeRef ? `2px solid ${theme.palette.primary.main}` : '2px solid transparent',
              backgroundColor: a.ref === activeRef ? theme.palette.action.selected : undefined,
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
            <div style={{
              minWidth: 0,
              flex: 1,
            }}
            >
              <Typography variant="body2" noWrap title={a.contract}>{a.contract}</Typography>
              <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                {[a.statusLabel, a.subtitle].filter(Boolean).join(' · ')}
              </Typography>
            </div>
            <Button
              size="small"
              variant="contained"
              color="primary"
              onClick={() => onSelect(a.ref)}
              sx={{ flexShrink: 0 }}
            >
              {t('View')}
            </Button>
          </div>
        ))}
      </div>
    </Paper>
  );
};

export default FindingDetailPanel;
