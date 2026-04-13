import { useMemo } from 'react';

import { type ExecutionTraceOutput } from '../../../../../../utils/api-types';
import AgentTraces from './AgentTraces';
import MainTraces from './MainTraces';

interface Props { tracesByAgent: ExecutionTraceOutput[] }

const EndpointTraces = ({ tracesByAgent }: Props) => {
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
    <div>
      {groupedTraces.map(([agentId, traces]) => (
        agentId === 'unknown'
          ? <MainTraces key="unknown" traces={traces} />
          : <AgentTraces key={agentId} traces={traces} />
      ))}
    </div>
  );
};

export default EndpointTraces;
