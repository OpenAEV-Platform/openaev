import { TrackChangesOutlined } from '@mui/icons-material';
import { alpha, Box, Button, IconButton, Popover, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type ExpectationsDriftOutput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';

interface Props {
  drift: ExpectationsDriftOutput | null;
  variant: 'scenario' | 'simulation' | 'atomic';
  onRealign: () => Promise<void>;
  /**
   * Persists the dismissal in database (shared between users, unlike local
   * storage) and refreshes the drift report. Realignment resets it server-side.
   */
  onDismiss: (dismissed: boolean) => Promise<void>;
  /**
   * The parent renders this component in two slots: the full warning button
   * before the Configuration button ("warning"), and the discreet dismissed
   * icon after it ("dismissed"). Each slot self-hides when the drift state
   * belongs to the other one, so ordering stays declarative in the header.
   */
  placement: 'warning' | 'dismissed';
}

/**
 * Discrete, hero-friendly warning surfaced only when the expectations stored in
 * the injects no longer match the predefined expectations of their injector
 * contracts (the security posture templates evolved since the injects inherited
 * them). Not a red alert - the user may have customized expectations on
 * purpose - but a nudge with a one-click bulk realignment. Renders nothing when
 * everything is aligned.
 *
 * <p>When the drift is deliberate, the warning can be dismissed: it collapses
 * into a small primary-colored icon button (rendered after the Configuration
 * button) whose tooltip recalls that the expectations still do not match.
 * Clicking it reopens the popover, where the drift can still be realigned or
 * the full warning restored. The dismissal lives in database so it is shared
 * between users, and a realignment clears it.
 */
const ExpectationsDriftIndicator: FunctionComponent<Props> = ({ drift, variant, onRealign, onDismiss, placement }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [realigning, setRealigning] = useState(false);
  const [dismissing, setDismissing] = useState(false);

  if (!drift?.drift_detected) {
    return null;
  }
  const dismissed = drift.drift_dismissed;
  if ((placement === 'warning') === dismissed) {
    return null;
  }

  const accent = theme.palette.warning.main;
  const count = drift.drifted_inject_count;
  const total = drift.total_inject_count;

  const detail = variant === 'atomic'
    ? t('The expectations of this atomic testing no longer match the validation requirements defined by its action.')
    : t('{count} of {total} injects use expectations that no longer match the validation requirements defined by their actions.', {
        count,
        total,
      });

  const submitRealign = async () => {
    setRealigning(true);
    try {
      await onRealign();
      MESSAGING$.notifySuccess(t('Expectations successfully realigned'));
      setAnchorEl(null);
    } catch {
      // The API layer already notified the user (simplePostCall rethrows after
      // notifying); swallow the rejection and keep the popover open for retry.
    } finally {
      setRealigning(false);
    }
  };

  const submitDismiss = async (nextDismissed: boolean) => {
    setDismissing(true);
    try {
      await onDismiss(nextDismissed);
      if (nextDismissed) {
        MESSAGING$.notifySuccess(t('Drift warning dismissed for all users'));
      }
      setAnchorEl(null);
    } catch {
      // The API layer already notified the user; keep the popover open for retry.
    } finally {
      setDismissing(false);
    }
  };

  return (
    <>
      {dismissed
        ? (
            <Tooltip title={t('Expectation drift dismissed: expectations still do not match their threat arsenal templates. Click to review and realign.')}>
              <IconButton
                size="small"
                color="primary"
                aria-label={t('Review expectations')}
                onClick={(event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget)}
              >
                <TrackChangesOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
          )
        : (
            <Button
              size="small"
              variant="outlined"
              startIcon={<TrackChangesOutlined sx={{ fontSize: 16 }} />}
              onClick={(event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget)}
              sx={{
                'lineHeight': 'initial',
                'whiteSpace': 'nowrap',
                'color': accent,
                'borderColor': alpha(accent, 0.4),
                'backgroundColor': alpha(accent, 0.08),
                '&:hover': {
                  borderColor: accent,
                  backgroundColor: alpha(accent, 0.14),
                },
              }}
            >
              {t('Review expectations')}
            </Button>
          )}
      <Popover
        open={!!anchorEl}
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
            variant: 'outlined',
            sx: {
              marginTop: 1,
              width: 420,
              maxWidth: '90vw',
              borderRadius: 1,
              padding: 2,
            },
          },
        }}
      >
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.12em',
          textTransform: 'uppercase',
          color: 'text.secondary',
          marginBottom: 1.5,
        }}
        >
          {t('Validation requirements')}
        </Typography>
        <Box sx={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 1.25,
          padding: 1,
          borderRadius: 1,
          border: `1px solid ${alpha(accent, 0.25)}`,
          background: alpha(accent, 0.05),
          marginBottom: 1.5,
        }}
        >
          <Box sx={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            flexShrink: 0,
            marginTop: 0.75,
            background: accent,
            boxShadow: `0 0 6px ${alpha(accent, 0.6)}`,
          }}
          />
          <Box sx={{
            flex: 1,
            minWidth: 0,
          }}
          >
            <Typography sx={{
              fontSize: 13,
              fontWeight: 600,
            }}
            >
              {t('Security posture requirements have changed')}
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {detail}
            </Typography>
            <Typography
              variant="body2"
              sx={{
                color: 'text.secondary',
                marginTop: 0.5,
              }}
            >
              {dismissed
                ? t('This warning has been dismissed for all users, but the expectations still do not match. You can realign them or restore the full warning.')
                : t('If these expectations were not customized on purpose, realign them to apply the current threat arsenal templates.')}
            </Typography>
          </Box>
        </Box>
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          {/* Deliberate drift escape hatch: acknowledge without hiding forever. */}
          <Button
            size="small"
            variant="text"
            disabled={dismissing || realigning}
            onClick={() => submitDismiss(!dismissed)}
            sx={{
              marginRight: 'auto',
              color: 'text.secondary',
            }}
          >
            {dismissed ? t('Restore warning') : t('Dismiss')}
          </Button>
          <Button size="small" variant="outlined" onClick={() => setAnchorEl(null)}>
            {t('Cancel')}
          </Button>
          <Button
            size="small"
            variant="contained"
            color="warning"
            disabled={realigning || dismissing}
            onClick={submitRealign}
          >
            {t('Realign expectations')}
          </Button>
        </Box>
      </Popover>
    </>
  );
};

export default ExpectationsDriftIndicator;
