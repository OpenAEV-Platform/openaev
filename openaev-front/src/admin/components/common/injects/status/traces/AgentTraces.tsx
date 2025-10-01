import { ArrowDropDownSharp, ArrowRightSharp } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useMemo, useState } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import ItemStatus from '../../../../../../components/ItemStatus';
import { type ExecutionTraceOutput } from '../../../../../../utils/api-types';
import ExecutionTime from './ExecutionTime';
import TraceMessage from './TraceMessage';

interface Props {
  traces: ExecutionTraceOutput[];
  isInitialExpanded?: boolean;
}

const AgentTraces = ({ traces, isInitialExpanded = false }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [isExpanded, setIsExpanded] = useState(isInitialExpanded);

  const toggleExpand = () => setIsExpanded(prev => !prev);

  const agentStatus = useMemo(() => {
    const sorted = [...traces].sort(
      (a, b) => new Date(a.execution_time).getTime() - new Date(b.execution_time).getTime(),
    );

    const finalTrace = sorted.find(t => t.execution_action === 'COMPLETE') ?? null;
    const startTrace = sorted.find(t => t.execution_action === 'START') ?? null;

    const agent = sorted[0]?.execution_agent;

    return {
      agentName: agent?.agent_executed_by_user,
      executorName: agent?.agent_executor?.executor_name,
      executorType: agent?.agent_executor?.executor_type,
      statusName: finalTrace?.execution_status ?? 'PENDING',
      trackingStart: startTrace?.execution_time,
      trackingEnd: finalTrace?.execution_time,
      traces: sorted,
    };
  }, [traces]);

  const tracesByAction = useMemo(() => {
    const grouped: {
      action: string;
      traces: ExecutionTraceOutput[];
    }[] = [];

    agentStatus.traces.forEach((trace) => {
      const last = grouped[grouped.length - 1];
      if (last && trace.execution_action === last.action) {
        last.traces.push(trace);
      } else {
        grouped.push({
          action: trace.execution_action,
          traces: [trace],
        });
      }
    });

    return grouped;
  }, [agentStatus.traces]);

  return (
    <>
      <div
        onClick={toggleExpand}
        style={{
          marginTop: theme.spacing(3),
          cursor: 'pointer',
          display: 'flex',
        }}
      >
        {isExpanded ? <ArrowDropDownSharp /> : <ArrowRightSharp />}
        <Typography gutterBottom sx={{ marginRight: theme.spacing(1.5) }}>
          {agentStatus.agentName}
        </Typography>
        <ItemStatus
          isInject
          status={agentStatus.statusName}
          label={agentStatus.statusName}
        />
      </div>

      {isExpanded && (
        <div style={{
          marginLeft: theme.spacing(3),
          marginTop: theme.spacing(1),
        }}
        >
          <ExecutionTime
            startDate={agentStatus.trackingStart}
            endDate={agentStatus.trackingEnd}
          />
          <div style={{
            display: 'flex',
            gap: theme.spacing(1.5),
          }}
          >
            <Typography variant="h3">{t('Executor')}</Typography>
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
            <Typography variant="body2">{t(agentStatus.executorName || '-')}</Typography>
          </div>
          <Typography variant="h3" sx={{ marginTop: theme.spacing(2) }}>
            {t('Traces')}
          </Typography>
          {tracesByAction.map((group, index) => (
            <div key={`trace-group-${index}`}>
              <Typography variant="h3" gutterBottom>
                {t(group.action)}
              </Typography>
              <TraceMessage traces={group.traces} />
            </div>
          ))}
        </div>
      )}
    </>
  );
};

export default AgentTraces;
