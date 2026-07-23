import { type FunctionComponent, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { getInjectStatusWithGlobalExecutionTraces } from '../../../../actions/injects/inject-action';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type InjectStatusOutput } from '../../../../utils/api-types';
import GlobalExecutionTraces from '../../common/injects/status/traces/GlobalExecutionTraces';

// "Execution details" tab: the global execution report (start / end / duration
// stat pills + the full traces timeline), shared by the atomic testing page and
// the simulation inject result page.
const AtomicTestingExecutionDetails: FunctionComponent = () => {
  const { t } = useFormatter();
  const { injectId } = useParams();
  const [loading, setLoading] = useState<boolean>(true);
  const [injectStatus, setInjectStatus] = useState<InjectStatusOutput>();

  useEffect(() => {
    if (!injectId) return;
    setLoading(true);
    getInjectStatusWithGlobalExecutionTraces(injectId)
      .then((response: { data: InjectStatusOutput }) => setInjectStatus(response.data))
      .finally(() => setLoading(false));
  }, [injectId]);

  if (loading) {
    return <Loader variant="inElement" />;
  }

  return injectStatus
    ? <GlobalExecutionTraces injectStatus={injectStatus} />
    : <Empty message={t('No data available')} />;
};

export default AtomicTestingExecutionDetails;
