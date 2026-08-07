import { useEffect, useMemo, useState } from 'react';

import { fetchExecutionDetail } from '../../../../../actions/attack-path/attack-path-actions';
import useFetchInjectExecutionResult from '../../../../../actions/inject_status/useFetchInjectExecutionResult';
import { getInjectStatusWithGlobalExecutionTraces } from '../../../../../actions/injects/inject-action';
import type { InjectStatus as InjectStatusType, InjectStatusOutput } from '../../../../../utils/api-types';
import InjectStatus from '../../../common/injects/status/InjectStatus';
import TraceStatusChip from '../../../common/injects/status/traces/TraceStatusChip';
import useAgentStatus from '../../../common/injects/status/traces/useAgentStatus';
import useResolvedAssetTarget from './useResolvedAssetTarget';

// Per-target execution status for a payload-backed execution (issue 244): the prevention/detection
// verdicts shown elsewhere answer "was it caught?", never "did it run at all?" — a technical failure
// (timeout, access denied…) and a clean run that simply went undetected previously looked identical.
// Resolves the same target + traces the live terminal view does (a second, independent fetch — this
// badge and that view render independently of one another), and renders nothing once resolved without
// any real traces (a seeded/demo snapshot has no live target to match).
export const PayloadExecutionStatusBadge = ({ injectId, endpointName }: {
  injectId: string;
  endpointName?: string;
}) => {
  const { target } = useResolvedAssetTarget(injectId, endpointName);
  const { injectExecutionResult } = useFetchInjectExecutionResult(injectId, target);
  const allTraces = useMemo(
    () => Object.values(injectExecutionResult?.execution_traces ?? {}).flat(),
    [injectExecutionResult],
  );
  const agentStatus = useAgentStatus(allTraces);
  if (allTraces.length === 0) {
    return null;
  }
  return <TraceStatusChip status={agentStatus.statusName} />;
};

// Inject-level execution status for a network injector (NetExec, Nmap…) (issue 244): its own traces have
// no `execution_agent` (no deployed agent), so the shared traces view routes them through a plain trace
// list with no status chip of its own. Fetched independently and rendered wherever this execution's
// status needs to be visible at a glance.
export const InjectorExecutionStatusBadge = ({ injectId }: { injectId: string }) => {
  const [statusName, setStatusName] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    getInjectStatusWithGlobalExecutionTraces(injectId)
      .then((response: { data: InjectStatusOutput }) => active && setStatusName(response.data?.status_name ?? null))
      .catch(() => active && setStatusName(null));
    return () => {
      active = false;
    };
  }, [injectId]);
  if (!statusName) {
    return null;
  }
  return <InjectStatus status={statusName as InjectStatusType['status_name']} />;
};

// An inject-level status the backend cannot still change under us: anything else (a run in flight, or
// a status the graph snapshot froze before it settled) is refined by this component's own fetch.
const TERMINAL_INJECT_STATUSES = new Set(['EXECUTED', 'PARTIAL', 'ERROR']);

// One execution row (endpoint/injector panel list). The graph row now ships this execution's inject,
// payload and inject-level status (see AttackPathGraphService#applyExecutionStatuses), so a network
// injector's status renders on FIRST PAINT — it used to cost two sequential fetches per visible row
// (detail for the injectId, then the inject's status) and showed nothing for a second or two.
//
// Two cases still fetch:
//   - a payload-backed execution, because "did it run" is per AGENT there, read from that target's
//     traces; the inject-level status cannot answer it for one agent among several;
//   - a row with no status, or a non-terminal one (a live run): the graph's value would go stale until
//     the next delta touching that row, so it is refined here rather than trusted.
export const ExecutionRowStatusBadge = ({ simulationId, executionRef, endpointName, injectId, payloadId, executionStatus }: {
  simulationId: string;
  executionRef?: string;
  endpointName?: string;
  injectId?: string;
  payloadId?: string;
  executionStatus?: string;
}) => {
  const settledFromGraph = !!injectId
    && !payloadId
    && !!executionStatus
    && TERMINAL_INJECT_STATUSES.has(executionStatus.toUpperCase());
  const [detail, setDetail] = useState<{
    injectId?: string;
    payloadId?: string;
  } | null>(injectId
    ? {
        injectId,
        payloadId,
      }
    : null);
  useEffect(() => {
    let active = true;
    if (!executionRef || settledFromGraph) {
      return () => {
        active = false;
      };
    }
    fetchExecutionDetail(simulationId, executionRef)
      .then(r => active && setDetail(r.data))
      .catch(() => active && setDetail(null));
    return () => {
      active = false;
    };
  }, [simulationId, executionRef, settledFromGraph]);
  if (settledFromGraph) {
    return <InjectStatus status={executionStatus as InjectStatusType['status_name']} />;
  }
  if (!detail?.injectId) {
    return null;
  }
  return detail.payloadId
    ? <PayloadExecutionStatusBadge injectId={detail.injectId} endpointName={endpointName} />
    : <InjectorExecutionStatusBadge injectId={detail.injectId} />;
};
