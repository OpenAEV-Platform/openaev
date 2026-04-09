import { useMemo } from 'react';

import { type ExecutionTraceOutput } from '../../../../../../utils/api-types';

export interface TraceGroup {
  action: string;
  traces: ExecutionTraceOutput[];
}

export interface AgentStatus {
  agentName?: string;
  executorName?: string;
  executorType?: string;
  statusName: string;
  trackingStart?: string;
  trackingEnd?: string;
  traces: ExecutionTraceOutput[];
  tracesByAction: TraceGroup[];
}

const useAgentStatus = (traces: ExecutionTraceOutput[]): AgentStatus => {
  return useMemo(() => {
    const sorted = [...traces].sort(
      (a, b) => new Date(a.execution_time).getTime() - new Date(b.execution_time).getTime(),
    );

    const finalTrace = sorted.find(t => t.execution_action === 'COMPLETE') ?? null;
    const startTrace = sorted.find(t => t.execution_action === 'START') ?? null;
    const agent = sorted[0]?.execution_agent;

    const grouped: TraceGroup[] = [];
    sorted.forEach((trace) => {
      const last = grouped.at(-1);
      if (last && trace.execution_action === last.action) {
        last.traces.push(trace);
      } else {
        grouped.push({
          action: trace.execution_action,
          traces: [trace],
        });
      }
    });

    return {
      agentName: agent?.agent_executed_by_user,
      executorName: agent?.agent_executor?.executor_name,
      executorType: agent?.agent_executor?.executor_type,
      statusName: finalTrace?.execution_status ?? 'Unknown',
      trackingStart: startTrace?.execution_time ?? sorted[0]?.execution_time,
      trackingEnd: finalTrace?.execution_time ?? sorted.at(-1)?.execution_time,
      traces: sorted,
      tracesByAction: grouped,
    };
  }, [traces]);
};

export default useAgentStatus;
