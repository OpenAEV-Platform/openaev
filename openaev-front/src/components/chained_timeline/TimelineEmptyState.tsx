import { Paper } from '@filigran/design-system';
import { ViewTimelineOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import ButtonCreate from '../common/ButtonCreate';
import { useFormatter } from '../i18n';

interface Props {
  canManage: boolean;
  onCreate: () => void;
}

/**
 * Zero-state of the interactive timeline (the old view rendered nothing at
 * all with no injects): explains the time-based canvas and offers the first
 * creation entry point.
 */
const TimelineEmptyState = ({ canManage, onCreate }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <Paper
      padding={0}
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
      }}
    >
      <Box sx={{
        width: 56,
        height: 56,
        display: 'grid',
        placeItems: 'center',
        borderRadius: 1,
        backgroundColor: alpha(theme.palette.primary.main, 0.08),
        border: `1px solid ${alpha(theme.palette.primary.main, 0.3)}`,
      }}
      >
        <ViewTimelineOutlined sx={{
          fontSize: 28,
          color: theme.palette.primary.main,
        }}
        />
      </Box>
      <Typography variant="h6">{t('No injects in this timeline yet')}</Typography>
      <Typography
        variant="body2"
        sx={{
          color: theme.palette.text.secondary,
          maxWidth: 460,
          textAlign: 'center',
        }}
      >
        {t('Design your attack over time: click anywhere on the timeline to schedule an inject at that exact moment, then chain injects together by connecting them.')}
      </Typography>
      {canManage && (
        <ButtonCreate label={t('Create your first inject')} onClick={onCreate} />
      )}
    </Paper>
  );
};

export default TimelineEmptyState;
