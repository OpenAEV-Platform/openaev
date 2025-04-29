import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
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
  const theme = useTheme();
  const [traces, setTraces] = useState<ExecutionTraceOutput[]>([]);
  const [loading, setLoading] = useState(false);

  const isTeam = target?.targetType === 'TEAMS';
  const isPlayer = target?.targetType === 'PLAYER';
  const isAsset = target?.targetType === 'ASSETS';
  const isAgent = target?.targetType === 'AGENT';

  useEffect(() => {
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
    fetchTraces();
  }, [injectId, target]);

  if (loading) {
    return <Loader />;
  }

  if (traces && traces.length === 0) {
    return <Empty message={t('No traces on this target.')} />;
  }

  return (
    <>
      {!loading && traces && traces.length > 0 && (
        <>
          <Typography variant="h4">{t('Execution logs')}</Typography>
          <Paper variant="outlined" style={{ padding: theme.spacing(0, 3, 3) }}>
            <>
              {(isTeam || isPlayer) && (<MainTraces traces={traces} />)}
              {isAsset && (<EndpointTraces key={target.id} endpoint={target} tracesByAgent={traces} />)}
              {isAgent && (<AgentTraces traces={traces} isInitialExpanded />)}
            </>
          </Paper>
        </>
      )}
    </>
  )
  ;
};

export default ExecutionStatusDetail;
