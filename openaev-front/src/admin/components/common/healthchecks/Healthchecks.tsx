import { Circle, ExpandMore } from '@mui/icons-material';
import { Accordion, AccordionDetails, AccordionSummary, Button, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import type { HealthCheck } from '../../../../utils/api-types';

interface Props {
  healthchecks: HealthCheck[];
  scenarioId: string;
}

const Healthchecks = ({ healthchecks, scenarioId }: Props) => {
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
        navigate(`/admin/scenarios/${scenarioId}/injects`);
        break;
      }
      case 'TEAMS': {
        navigate(`/admin/scenarios/${scenarioId}/definition`);
        break;
      }
      case 'NMAP':
      case 'NUCLEI': {
        window.open(`${documentationRootUrl}/latest/usage/injectors`);
        break;
      }
      default:
        return;
    }
  };

  return (
    <div style={{
      width: '100%',
      display: 'flex',
      marginBottom: theme.spacing(2),
    }}
    >
      <div style={{
        backgroundColor: getPaperInformationBarColor(),
        borderBottomLeftRadius: 5,
        borderTopLeftRadius: 5,
        height: 'auto',
        width: '2px',
      }}
      />
      <Accordion
        defaultExpanded
        style={{
          width: '100%',
          margin: 0,
        }}
      >
        <AccordionSummary expandIcon={<ExpandMore />}>
          <Typography variant="h1" marginBottom={0}>{t('Scenario configuration')}</Typography>
        </AccordionSummary>
        <AccordionDetails style={{
          display: 'flex',
          flexDirection: 'column',
        }}
        >
          {orderedHealthchecks.map((healthcheck: HealthCheck, index: number) => {
            return (
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
            );
          })}
        </AccordionDetails>
      </Accordion>
    </div>
  );
};

export default Healthchecks;
