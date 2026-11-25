import { Circle } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import type { HealthCheck } from '../../../../utils/api-types';
import AlertBanner from '../AlertBanner';
import HealthcheckFixButton from './HealthcheckFixButton';

interface Props {
  healthchecks: HealthCheck[];
  scenarioId?: string;
  exerciseId?: string;
}

const Healthchecks = ({ healthchecks, scenarioId, exerciseId }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const orderedHealthchecks = healthchecks.length ? healthchecks.sort((a, b) => a.status === 'ERROR' && b.status !== 'ERROR' ? -1 : 1) : [];

  const getPaperInformationBarColor = (): string => {
    if (!healthchecks?.length) {
      return theme.palette.primary.main;
    } else {
      return healthchecks.find(healthcheck => healthcheck.status === 'ERROR') ? theme.palette.error.main : theme.palette.warning.main;
    }
  };

  return (
    <div style={{ marginBottom: theme.spacing(2) }}>
      <AlertBanner
        color={getPaperInformationBarColor()}
        title={t(exerciseId ? 'Simulation configuration' : 'Scenario configuration')}
      >
        {orderedHealthchecks.map((healthcheck: HealthCheck, index: number) => (
          <div
            key={'scenario-healthcheck-' + index}
            style={{
              alignItems: 'center',
              display: 'flex',
              gap: theme.spacing(1),
            }}
          >
            <Circle
              sx={{
                color: healthcheck.status === 'ERROR' ? theme.palette.error.main : theme.palette.warning.main,
                height: '10px',
              }}
            />
            <Typography
              variant="h3"
              sx={{ marginBottom: 0 }}
            >
              {t(`healthcheck.type.${healthcheck.type}`)}
              :
            </Typography>
            <span>{t(`healthcheck.description.${healthcheck.type}.${healthcheck.detail}`)}</span>
            <HealthcheckFixButton
              healthcheck={healthcheck}
              owner={{
                scenarioId,
                exerciseId,
              }}
              priority="tertiary"
            />
          </div>
        ))}
      </AlertBanner>
    </div>
  );
};

export default Healthchecks;
