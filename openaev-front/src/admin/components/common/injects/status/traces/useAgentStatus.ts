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
    // Chronological order, so a timeout shows before the traces that arrive after it.
    // The action order only breaks ties between traces received at the same time.
    const sorted = [...traces].sort(
      (a, b) => new Date(a.execution_time).getTime() - new Date(b.execution_time).getTime()
        || actionOrder(a.execution_action) - actionOrder(b.execution_action),
    );

    const finalTrace = sorted.find(t => t.execution_action === 'COMPLETE') ?? null;
    const lastExecutionTrace = [...sorted].reverse().find(t => t.execution_action === 'EXECUTION') ?? null;
    const agent = sorted[0]?.execution_agent;

    // One group per action, ordered by its first trace: a timeout received before the first
    // attack trace shows first, and a late trace joins its action group instead of opening a new one
    const groupsByAction = new Map<string, TraceGroup>();
    sorted.forEach((trace) => {
      const group = groupsByAction.get(trace.execution_action);
      if (group) {
        group.traces.push(trace);
      } else {
        groupsByAction.set(trace.execution_action, {
          action: trace.execution_action,
          traces: [trace],
        });
      }
    });
    const grouped = [...groupsByAction.values()];

    // Use COMPLETE trace status, or fall back to last EXECUTION trace status
    const statusName = finalTrace?.execution_status
      ?? lastExecutionTrace?.execution_status
      ?? 'Unknown';

    return {
      agentName: agent?.agent_executed_by_user,
      executorName: agent?.agent_executor?.executor_name,
      executorType: agent?.agent_executor?.executor_type,
      statusName,
      // The first trace, START or not: a timeout can be the only trace received
      trackingStart: sorted[0]?.execution_time,
      trackingEnd: finalTrace?.execution_time,
      traces: sorted,
      tracesByAction: grouped,
    };
  }, [traces]);
};

export default useAgentStatus;
