import { Alert } from '@mui/material';
import { useEffect, useState } from 'react';

import { getInjectStatusWithGlobalExecutionTraces, getInjectTracesFromInjectAndTarget } from '../../../../../actions/injects/inject-action';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { type ExecutionTraceOutput, type InjectStatusOutput } from '../../../../../utils/api-types';
import AgentTraces from './traces/AgentTraces';
import EndpointTraces from './traces/EndpointTraces';
import MainTraces from './traces/MainTraces';
import TraceMessage from './traces/TraceMessage';

interface Props {
  injectId: string;
  target?: {
    id: string;
    name?: string;
    targetType: string;
    platformType?: string;
  };
  /** Inject-level execution status, used to surface global failure traces when the target has none. */
  injectStatusName?: string;
}

// Inject-level statuses for which a target with no traces of its own is worth
// explaining with the global execution report (pre-execution failures).
const INJECT_ERROR_STATUSES = ['ERROR', 'PARTIAL'];

const ExecutionStatusDetail = ({ injectId, target, injectStatusName }: Props) => {
  const { t } = useFormatter();
  const [traces, setTraces] = useState<ExecutionTraceOutput[]>([]);
  const [globalErrorTraces, setGlobalErrorTraces] = useState<ExecutionTraceOutput[]>([]);
  const [loading, setLoading] = useState(false);

  const isTeam = target?.targetType === 'TEAMS';
  const isPlayer = target?.targetType === 'PLAYERS';
  // AI targets are assets (AiTarget extends Asset); their traces are asset-scoped.
  const isAsset = target?.targetType === 'ASSETS' || target?.targetType === 'AI_TARGETS';
  const isAgent = target?.targetType === 'AGENT';

  const fetchTraces = async () => {
    if (!target?.id || !target.targetType) return;
    setLoading(true);
    try {
      const result = await getInjectTracesFromInjectAndTarget(injectId, target.id, target.targetType);
      const targetTraces: ExecutionTraceOutput[] = result.data || [];
      setTraces(targetTraces);
      // Pre-execution failures (unmet dependencies, health-check or executor errors) are
      // recorded as GLOBAL inject traces (no agent / target), so the per-target endpoint
      // returns nothing and the Overview would just say "No traces on this target". Fetch the
      // global report and surface its error trace(s) here so the failure reason is visible
      // without opening the Execution details tab.
      if (targetTraces.length === 0 && injectStatusName && INJECT_ERROR_STATUSES.includes(injectStatusName)) {
        const statusResult: { data: InjectStatusOutput } = await getInjectStatusWithGlobalExecutionTraces(injectId);
        setGlobalErrorTraces(statusResult.data?.status_main_traces ?? []);
      } else {
        setGlobalErrorTraces([]);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTraces();
  }, [injectId, target?.id, target?.targetType, injectStatusName]);

  if (loading) {
    return <Loader variant="inElement" />;
  }

  if (traces && traces.length === 0) {
    if (globalErrorTraces.length > 0) {
      return (
        <>
          <Alert severity="error" variant="outlined" sx={{ marginBottom: 2 }}>
            {t('The inject failed before reaching this target. See the reason below.')}
          </Alert>
          <TraceMessage traces={globalErrorTraces} />
        </>
      );
    }
    return <Empty message={t('No traces on this target.')} />;
  }

  // No wrapping card here: the parent section header already delimits the block,
  // and the per-agent card (AgentTraces) provides the single, subtle outline
  // around the timeline. This avoids stacking multiple outlined boxes.
  return (
    <>
      {!loading && traces && traces.length > 0 && (
        <>
          {(isTeam || isPlayer) && (<MainTraces traces={traces} />)}
          {isAsset && (<EndpointTraces key={target.id} tracesByAgent={traces} />)}
          {isAgent && (<AgentTraces traces={traces} />)}
        </>
      )}
    </>
  );
};

export default ExecutionStatusDetail;
