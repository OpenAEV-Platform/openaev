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

const ACTION_DISPLAY_ORDER: Record<string, number> = {
  START: 0,
  PREREQUISITE_CHECK: 1,
  PREREQUISITE_EXECUTION: 2,
  EXECUTION: 3,
  CLEANUP_EXECUTION: 4,
  COMPLETE: 5,
};

const actionOrder = (action: string): number => ACTION_DISPLAY_ORDER[action] ?? 99;

const useAgentStatus = (traces: ExecutionTraceOutput[]): AgentStatus => {
  return useMemo(() => {
    const sorted = [...traces].sort(
      (a, b) => actionOrder(a.execution_action) - actionOrder(b.execution_action)
        || new Date(a.execution_time).getTime() - new Date(b.execution_time).getTime(),
    );

    const finalTrace = sorted.find(t => t.execution_action === 'COMPLETE') ?? null;
    const startTrace = sorted.find(t => t.execution_action === 'START') ?? null;
    const lastExecutionTrace = [...sorted].reverse().find(t => t.execution_action === 'EXECUTION') ?? null;
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

    // Use COMPLETE trace status, or fall back to last EXECUTION trace status
    const statusName = finalTrace?.execution_status
      ?? lastExecutionTrace?.execution_status
      ?? 'Unknown';

    return {
      agentName: agent?.agent_executed_by_user,
      executorName: agent?.agent_executor?.executor_name,
      executorType: agent?.agent_executor?.executor_type,
      statusName,
      trackingStart: startTrace?.execution_time,
      trackingEnd: finalTrace?.execution_time,
      traces: sorted,
      tracesByAction: grouped,
    };
  }, [traces]);
};

export default useAgentStatus;
