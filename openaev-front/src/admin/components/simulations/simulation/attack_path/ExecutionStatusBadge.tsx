import { useEffect, useMemo, useState } from 'react';

import { fetchExecutionDetail } from '../../../../../actions/attack-path/attack-path-actions';
import useFetchInjectExecutionResult from '../../../../../actions/inject_status/useFetchInjectExecutionResult';
import { getInjectStatusWithGlobalExecutionTraces, searchTargets } from '../../../../../actions/injects/inject-action';
import type { InjectStatus as InjectStatusType, InjectStatusOutput, InjectTarget } from '../../../../../utils/api-types';
import InjectStatus from '../../../common/injects/status/InjectStatus';
import TraceStatusChip from '../../../common/injects/status/traces/TraceStatusChip';
import useAgentStatus from '../../../common/injects/status/traces/useAgentStatus';

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
  const [target, setTarget] = useState<InjectTarget | null>(null);
  useEffect(() => {
    let active = true;
    searchTargets(injectId, 'ASSETS', {
      filterGroup: {
        mode: 'and',
        filters: [],
      },
      size: 50,
      page: 0,
    })
      .then((response) => {
        if (!active) {
          return;
        }
        const targets: InjectTarget[] = response.data?.content ?? [];
        const match = targets.find(tg => tg.target_name && tg.target_name === endpointName);
        setTarget(match ?? targets[0] ?? null);
      })
      .catch(() => active && setTarget(null));
    return () => {
      active = false;
    };
  }, [injectId, endpointName]);
  const { injectExecutionResult } = useFetchInjectExecutionResult(injectId, target ?? ({} as InjectTarget));
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

// One execution row (endpoint/injector panel list) rarely has its injectId/payloadId already loaded —
// those live on the execution DETAIL DTO, not the lightweight graph row — so this resolves them first
// (a small extra fetch per visible row) before delegating to the same two badges the Result tab uses,
// so every place an execution shows up agrees on the same "did it actually run" status.
export const ExecutionRowStatusBadge = ({ simulationId, executionRef, endpointName }: {
  simulationId: string;
  executionRef?: string;
  endpointName?: string;
}) => {
  const [detail, setDetail] = useState<{
    injectId?: string;
    payloadId?: string;
  } | null>(null);
  useEffect(() => {
    let active = true;
    if (!executionRef) {
      setDetail(null);
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
  }, [simulationId, executionRef]);
  if (!detail?.injectId) {
    return null;
  }
  return detail.payloadId
    ? <PayloadExecutionStatusBadge injectId={detail.injectId} endpointName={endpointName} />
    : <InjectorExecutionStatusBadge injectId={detail.injectId} />;
};
