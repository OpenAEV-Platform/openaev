import { Circle } from '@mui/icons-material';
import { Button, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import type { HealthCheck } from '../../../../utils/api-types';
import AlertBanner from '../AlertBanner';

interface Props {
  healthchecks: HealthCheck[];
  scenarioId?: string;
  exerciseId?: string;
}

const Healthchecks = ({ healthchecks, scenarioId, exerciseId }: Props) => {
  const documentationRootUrl = 'https://docs.openaev.io';
  const theme = useTheme();
  const navigate = useNavigate();
  const { t } = useFormatter();
  const orderedHealthchecks = healthchecks.length ? healthchecks.sort((a, b) => a.status === 'ERROR' && b.status !== 'ERROR' ? -1 : 1) : [];

  const getPaperInformationBarColor = (): string => {
    if (!healthchecks?.length) {
      return theme.palette.primary.main;
    } else {
      return healthchecks.find(healthcheck => healthcheck.status === 'ERROR') ? theme.palette.error.main : theme.palette.warning.main;
    }
  };

  const goToHealthcheckAction = (healthcheckType: string) => {
    switch (healthcheckType) {
      case 'SMTP': {
        window.open(`${documentationRootUrl}/latest/deployment/configuration/?h=smtp#mail-services`);
        break;
      }
      case 'IMAP': {
        window.open(`${documentationRootUrl}/latest/deployment/configuration/?h=smtp#imap`);
        break;
      }
      case 'AGENT_OR_EXECUTOR': {
        navigate('/admin/agents');
        break;
      }
      case 'SECURITY_SYSTEM_COLLECTOR': {
        window.open(`${documentationRootUrl}/latest/usage/collectors/?h=collector`);
        break;
      }
      case 'INJECT': {
        if (exerciseId) {
          navigate(`/admin/simulations/${exerciseId}/injects`);
        } else {
          navigate(`/admin/scenarios/${scenarioId}/injects`);
        }
        break;
      }
      case 'TEAMS': {
        if (exerciseId) {
          navigate(`/admin/simulations/${exerciseId}/definition`);
        } else {
          navigate(`/admin/scenarios/${scenarioId}/definition`);
        }
        break;
      }
      case 'NMAP':
      case 'NUCLEI': {
        window.open(`${documentationRootUrl}/latest/usage/injectors`);
        break;
      }
      case 'SCOPE_DEFINITION': {
        if (exerciseId) {
          navigate(`/admin/simulations/${exerciseId}/scope`);
        } else {
          navigate(`/admin/scenarios/${scenarioId}/scope`);
        }
        break;
      }
      default:
        return;
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
            <Typography variant="h3" marginBottom={0}>
              {t(`healthcheck.type.${healthcheck.type}`)}
              :
            </Typography>
            <span>{t(`healthcheck.description.${healthcheck.type}.${healthcheck.detail}`)}</span>
            <Button
              color="primary"
              size="small"
              onClick={() => goToHealthcheckAction(healthcheck.type!)}
            >
              {t(`healthcheck.button.${healthcheck.type}.${healthcheck.detail}`)}
            </Button>
          </div>
        ))}
      </AlertBanner>
    </div>
  );
};

export default Healthchecks;
