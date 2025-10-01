import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useMemo } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import PlatformIcon from '../../../../../../components/PlatformIcon';
import { type ExecutionTraceOutput } from '../../../../../../utils/api-types';
import AgentTraces from './AgentTraces';
import MainTraces from './MainTraces';

interface Props {
  endpoint: {
    id: string;
    name?: string;
    targetType: string;
    platformType?: string;
  };
  tracesByAgent: ExecutionTraceOutput[];
}

const EndpointTraces = ({ endpoint, tracesByAgent }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const groupedTraces = useMemo(() => {
    const grouped: Record<string, ExecutionTraceOutput[]> = {};

    for (const trace of tracesByAgent) {
      const agentId = trace.execution_agent?.agent_id ?? 'unknown';
      if (!grouped[agentId]) {
        grouped[agentId] = [];
      }
      grouped[agentId].push(trace);
    }

    return Object.entries(grouped).sort(([, a], [, b]) => {
      const nameA = a[0]?.execution_agent?.agent_executed_by_user ?? '';
      const nameB = b[0]?.execution_agent?.agent_executed_by_user ?? '';
      return nameA.localeCompare(nameB);
    });
  }, [tracesByAgent]);

  return (
    <div
      style={{
        display: 'grid',
        marginTop: theme.spacing(3),
        gap: theme.spacing(1),
      }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1.5),
      }}
      >
        <Typography margin="0" variant="h3">
          {t('Name')}
        </Typography>
        <Typography variant="body2">{endpoint.name}</Typography>
      </div>

      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1.5),
      }}
      >
        <Typography margin="0" variant="h3">
          {t('Type')}
        </Typography>
        <Typography variant="body2">{t('Endpoint')}</Typography>
      </div>

      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1.5),
      }}
      >
        <Typography margin="0" variant="h3">
          {t('Platform')}
        </Typography>
        <PlatformIcon
          key={endpoint.platformType}
          platform={endpoint.platformType || ''}
          tooltip
          width={16}
        />
        <Typography variant="body2">{t(endpoint.platformType || '-')}</Typography>
      </div>

      <div style={{ overflow: 'auto' }}>
        {groupedTraces.map(([agentId, traces]) => {
          if (agentId == 'unknown') {
            return <MainTraces key="unknown" traces={traces} />;
          } else {
            return <AgentTraces key={agentId} traces={traces} />;
          }
        })}
      </div>
    </div>
  );
};

export default EndpointTraces;
