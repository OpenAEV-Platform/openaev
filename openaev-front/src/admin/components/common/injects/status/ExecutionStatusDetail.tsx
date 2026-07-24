import { useEffect, useState } from 'react';

import { getInjectTracesFromInjectAndTarget } from '../../../../../actions/injects/inject-action';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { type ExecutionTraceOutput } from '../../../../../utils/api-types';
import AgentTraces from './traces/AgentTraces';
import EndpointTraces from './traces/EndpointTraces';
import MainTraces from './traces/MainTraces';

interface Props {
  injectId: string;
  target?: {
    id: string;
    name?: string;
    targetType: string;
    platformType?: string;
  };
}

const ExecutionStatusDetail = ({ injectId, target }: Props) => {
  const { t } = useFormatter();
  const [traces, setTraces] = useState<ExecutionTraceOutput[]>([]);
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
      setTraces(result.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTraces();
  }, [injectId, target?.id, target?.targetType]);

  if (loading) {
    return <Loader variant="inElement" />;
  }

  if (traces && traces.length === 0) {
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
