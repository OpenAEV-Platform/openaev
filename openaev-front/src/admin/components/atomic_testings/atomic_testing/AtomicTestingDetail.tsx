import { Typography } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { getInjectStatusWithGlobalExecutionTraces } from '../../../../actions/injects/inject-action';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type InjectResultOverviewOutput, type InjectStatusOutput } from '../../../../utils/api-types';
import GlobalExecutionTraces from '../../common/injects/status/traces/GlobalExecutionTraces';

const AtomicTestingDetail: FunctionComponent = () => {
  const { t } = useFormatter();
  const { injectId } = useParams() as { injectId: InjectResultOverviewOutput['inject_id'] };

  const [injectStatus, setInjectStatus] = useState<InjectStatusOutput | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    if (injectId) {
      setLoading(true);
      getInjectStatusWithGlobalExecutionTraces(injectId)
        .then(res => setInjectStatus(res.data))
        .finally(() => {
          setLoading(false);
        });
    }
  }, [injectId]);

  if (loading) {
    return <Loader />;
  }

  if (!injectStatus) {
    return <Typography variant="body1">{t('No data available')}</Typography>;
  }

  return <GlobalExecutionTraces injectStatus={injectStatus} />;
};

export default AtomicTestingDetail;
