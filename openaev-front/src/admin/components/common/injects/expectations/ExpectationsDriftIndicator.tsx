import { TrackChangesOutlined } from '@mui/icons-material';
import { alpha, Box, Button, Popover, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type ExpectationsDriftOutput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';

interface Props {
  drift: ExpectationsDriftOutput | null;
  variant: 'scenario' | 'simulation' | 'atomic';
  onRealign: () => Promise<void>;
}

/**
 * Discrete, hero-friendly warning surfaced only when the expectations stored in
 * the injects no longer match the predefined expectations of their injector
 * contracts (the security posture templates evolved since the injects inherited
 * them). Not a red alert - the user may have customized expectations on
 * purpose - but a nudge with a one-click bulk realignment. Renders nothing when
 * everything is aligned.
 */
const ExpectationsDriftIndicator: FunctionComponent<Props> = ({ drift, variant, onRealign }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [realigning, setRealigning] = useState(false);

  if (!drift?.drift_detected) {
    return null;
  }

  const accent = theme.palette.warning.main;
  const count = drift.drifted_inject_count;
  const total = drift.total_inject_count;

  const detail = variant === 'atomic'
    ? t('The expectations of this atomic testing no longer match the validation requirements defined by its threat arsenal item.')
    : t('{count} of {total} injects use expectations that no longer match the validation requirements defined by their threat arsenal items.', {
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

  return (
    <>
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
              {t('If these expectations were not customized on purpose, realign them to apply the current threat arsenal templates.')}
            </Typography>
          </Box>
        </Box>
        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 1,
        }}
        >
          <Button size="small" variant="outlined" onClick={() => setAnchorEl(null)}>
            {t('Cancel')}
          </Button>
          <Button
            size="small"
            variant="contained"
            color="warning"
            disabled={realigning}
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
