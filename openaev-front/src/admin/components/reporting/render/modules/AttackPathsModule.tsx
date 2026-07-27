import { ArrowForwardOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type AttackPathPhaseEntry, type ModuleDataState } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError } from './ModuleSection';

/**
 * Attack path summary: the kill chain phases traversed by the executed
 * injects, in phase order, with per-phase inject counts. A print-friendly
 * linear "kill chain journey" instead of the interactive graph.
 */

interface Props { attackPaths: ModuleDataState<AttackPathPhaseEntry[]> }

const AttackPathsModule: FunctionComponent<Props> = ({ attackPaths }) => {
  const theme = useTheme();
  const { t, n } = useFormatter();

  if (attackPaths.status === 'error') return <ModuleError />;
  if (attackPaths.status === 'unsupported') {
    return <ModuleEmpty message={t('Attack paths are not available for this report subject.')} />;
  }
  const phases = attackPaths.data ?? [];
  if (attackPaths.status !== 'success' || phases.length === 0) {
    return <ModuleEmpty message={t('No kill chain phase was traversed over the selected time range.')} />;
  }

  const maxCount = Math.max(...phases.map(phase => phase.count), 1);

  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'stretch',
      gap: 1.25,
      flexWrap: 'wrap',
    }}
    >
      {phases.map((phase, index) => (
        <Fragment key={phase.id}>
          {index > 0 && (
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
            }}
            >
              <ArrowForwardOutlined sx={{
                fontSize: 14,
                color: 'text.disabled',
              }}
              />
            </Box>
          )}
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 0.75,
            minWidth: 120,
            padding: 2,
            borderRadius: 1,
            border: `1px solid ${alpha(theme.palette.primary.main, 0.3)}`,
            backgroundColor: alpha(theme.palette.primary.main, 0.05),
          }}
          >
            <Typography sx={{
              fontSize: 10,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: 'text.secondary',
            }}
            >
              {phase.name}
            </Typography>
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 18,
              fontWeight: 600,
              lineHeight: 1,
            }}
            >
              {n(phase.count)}
            </Typography>
            <Box sx={{
              height: 3,
              borderRadius: 2,
              backgroundColor: alpha(theme.palette.text.primary, 0.08),
              overflow: 'hidden',
            }}
            >
              <Box sx={{
                width: `${(phase.count / maxCount) * 100}%`,
                height: '100%',
                backgroundColor: 'primary.main',
              }}
              />
            </Box>
            <Typography sx={{
              fontSize: 9,
              color: 'text.disabled',
            }}
            >
              {t('injects')}
            </Typography>
          </Box>
        </Fragment>
      ))}
    </Box>
  );
};

export default AttackPathsModule;
