import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import ExpandableSection from '../../../../../../components/common/ExpandableSection';
import { useFormatter } from '../../../../../../components/i18n';
import { type ExecutionTraceOutput } from '../../../../../../utils/api-types';
import AgentStatusHeader from './AgentStatusHeader';
import ExecutionTime from './ExecutionTime';
import TraceMessage from './TraceMessage';
import TraceStatusChip from './TraceStatusChip';
import useAgentStatus from './useAgentStatus';

interface Props {
  traces: ExecutionTraceOutput[];
  isInitialExpanded?: boolean;
}

const AgentTraces = ({ traces, isInitialExpanded = false }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const agentStatus = useAgentStatus(traces);

  return (
    <ExpandableSection
      forceExpanded={isInitialExpanded}
      header={<AgentStatusHeader agentName={agentStatus.agentName} statusName={agentStatus.statusName} />}
    >
      <div style={{ margin: theme.spacing(0, 2) }}>
        {isInitialExpanded && (
          <TraceStatusChip
            status={agentStatus.statusName}
          />
        )}
        <ExecutionTime
          startDate={agentStatus.trackingStart}
          endDate={agentStatus.trackingEnd}
        />
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr 1fr',
            padding: theme.spacing(0.5, 0),
          }}
        >
          <Typography
            variant="caption"
            sx={{
              color: 'text.secondary',
              textTransform: 'uppercase',
              letterSpacing: 0.5,
            }}
          >
            {t('Executor')}
          </Typography>
          {agentStatus.executorType && (
            <img
              src={`/api/images/executors/icons/${agentStatus.executorType}`}
              alt={agentStatus.executorType}
              style={{
                width: 20,
                height: 20,
                borderRadius: 4,
              }}
            />
          )}
        </div>
        <Typography
          variant="caption"
          sx={{
            color: 'text.secondary',
            textTransform: 'uppercase',
            letterSpacing: 0.5,
            display: 'block',
            marginTop: theme.spacing(0.5),
            marginBottom: theme.spacing(0.5),
          }}
        >
          {t('Traces')}
        </Typography>
        {agentStatus.tracesByAction.map((group, index) => (
          <div key={`trace-group-${index}`} style={{ marginBottom: theme.spacing(1) }}>
            <Typography
              variant="caption"
              sx={{ fontWeight: 600 }}
            >
              {t(group.action)}
            </Typography>
            <TraceMessage traces={group.traces} />
          </div>
        ))}
      </div>

    </ExpandableSection>
  );
};

export default AgentTraces;
