import { useEffect, useMemo, useState } from 'react';

import { fetchExecutionDetail } from '../../../../../actions/attack-path/attack-path-actions';
import useFetchInjectExecutionResult from '../../../../../actions/inject_status/useFetchInjectExecutionResult';
import { getInjectStatusWithGlobalExecutionTraces } from '../../../../../actions/injects/inject-action';
import { useFormatter } from '../../../../../components/i18n';
import type { InjectStatusOutput } from '../../../../../utils/api-types';
import { getInjectStatusLabel, getInjectStatusTooltip } from '../../../../../utils/statusLabels';
import TraceStatusChip from '../../../common/injects/status/traces/TraceStatusChip';
import useAgentStatus from '../../../common/injects/status/traces/useAgentStatus';
import ExecutionRanChip from './ExecutionRanChip';
import useResolvedAssetTarget from './useResolvedAssetTarget';

// Per-target execution status for a payload-backed execution (issue 244): the prevention/detection
// verdicts shown elsewhere answer "was it caught?", never "did it run at all?" — a technical failure
// (timeout, access denied…) and a clean run that simply went undetected previously looked identical.
// Resolves the same target + traces the live terminal view does (a second, independent fetch — this
// badge and that view render independently of one another).
//
// When that per-agent resolution comes back with no EXECUTION-action trace for the endpoint the panel
// picked, it falls back to the graph-shipped inject-level status rather than rendering nothing: an
// action whose traces do not link back to the resolved asset (or that recorded no EXECUTION-action
// trace) otherwise showed a blank Execution cell even though it clearly ran. Absent that fallback
// status too (a seeded/demo snapshot with no live inject), it keeps the historical empty render.
export const PayloadExecutionStatusBadge = ({ injectId, endpointName, fallbackStatus }: {
  injectId: string;
  endpointName?: string;
  fallbackStatus?: string;
}) => {
  const { t } = useFormatter();
  const { target, loading: targetLoading } = useResolvedAssetTarget(injectId, endpointName);
  const { injectExecutionResult, loading: resultLoading } = useFetchInjectExecutionResult(injectId, target);
  const allTraces = useMemo(
    () => Object.values(injectExecutionResult?.execution_traces ?? {}).flat(),
    [injectExecutionResult],
  );
  const agentStatus = useAgentStatus(allTraces);
  if (allTraces.length > 0) {
    return <TraceStatusChip status={agentStatus.statusName} />;
  }
  // Still settling: a resolved target whose result has not come back yet would flash the fallback for
  // a frame before the real per-agent chip. Wait until the target and its result have both settled.
  const resolving = targetLoading || resultLoading || (!!target && injectExecutionResult === undefined);
  if (resolving || !fallbackStatus) {
    return null;
  }
  return (
    <ExecutionRanChip
      status={fallbackStatus}
      label={t(getInjectStatusLabel(fallbackStatus))}
      tooltip={t(getInjectStatusTooltip(fallbackStatus))}
    />
  );
};

// Inject-level execution status for a network injector (NetExec, Nmap…) (issue 244): its own traces have
// no `execution_agent` (no deployed agent), so the shared traces view routes them through a plain trace
// list with no status chip of its own. Fetched independently and rendered wherever this execution's
// status needs to be visible at a glance.
export const InjectorExecutionStatusBadge = ({ injectId }: { injectId: string }) => {
  const { t } = useFormatter();
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
  return (
    <ExecutionRanChip
      status={statusName}
      label={t(getInjectStatusLabel(statusName))}
      tooltip={t(getInjectStatusTooltip(statusName))}
    />
  );
};

// An inject-level status the backend cannot still change under us: anything else (a run in flight, or
// a status the graph snapshot froze before it settled) is refined by this component's own fetch.
const TERMINAL_INJECT_STATUSES = new Set(['EXECUTED', 'PARTIAL', 'ERROR']);

// One execution row (endpoint/injector panel list). The graph row now ships this execution's inject,
// payload and inject-level status (see AttackPathGraphService#applyExecutionStatuses), so a network
// injector's status renders on FIRST PAINT — it used to cost two sequential fetches per visible row
// (detail for the injectId, then the inject's status) and showed nothing for a second or two.
//
// Two cases still resolve live, but from the graph-provided injectId, with no detail fetch:
//   - a payload-backed execution, because "did it run" is per AGENT there, read from that target's
//     traces; the inject-level status cannot answer it for one agent among several;
//   - a row with no status, or a non-terminal one (a live run): the graph's value would go stale until
//     the next delta touching that row, so it is refined here rather than trusted.
// The detail fetch only remains for a row the graph could not resolve (no injectId shipped).
export const ExecutionRowStatusBadge = ({ simulationId, executionRef, endpointName, injectId, payloadId, executionStatus }: {
  simulationId: string;
  executionRef?: string;
  endpointName?: string;
  injectId?: string;
  payloadId?: string;
  executionStatus?: string;
}) => {
  const { t } = useFormatter();
  const settledFromGraph = !!injectId
    && !payloadId
    && !!executionStatus
    && TERMINAL_INJECT_STATUSES.has(executionStatus.toUpperCase());
  // Fetched fallback for a row the graph shipped no injectId for. Derived per render from the props
  // otherwise, so a prop update (a delta filling the ids in) is picked up without a remount.
  const [fetchedDetail, setFetchedDetail] = useState<{
    injectId?: string;
    payloadId?: string;
  } | null>(null);
  useEffect(() => {
    let active = true;
    if (!executionRef || injectId) {
      setFetchedDetail(null);
      return () => {
        active = false;
      };
    }
    fetchExecutionDetail(simulationId, executionRef)
      .then(r => active && setFetchedDetail(r.data))
      .catch(() => active && setFetchedDetail(null));
    return () => {
      active = false;
    };
  }, [simulationId, executionRef, injectId]);
  if (settledFromGraph) {
    return (
      <ExecutionRanChip
        status={executionStatus}
        label={t(getInjectStatusLabel(executionStatus))}
        tooltip={t(getInjectStatusTooltip(executionStatus))}
      />
    );
  }
  const detail = injectId
    ? {
        injectId,
        payloadId,
      }
    : fetchedDetail;
  if (!detail?.injectId) {
    return null;
  }
  return detail.payloadId
    ? (
        <PayloadExecutionStatusBadge
          injectId={detail.injectId}
          endpointName={endpointName}
          fallbackStatus={executionStatus}
        />
      )
    : <InjectorExecutionStatusBadge injectId={detail.injectId} />;
};
