import { Typography } from '@mui/material';

import { useFormatter } from '../../../../../../components/i18n';
import { type ExecutionTraceOutput } from '../../../../../../utils/api-types';
import TraceMessage from './TraceMessage';

interface Props { traces?: ExecutionTraceOutput[] }

const MainTraces = ({ traces }: Props) => {
  const { t } = useFormatter();

  if (!traces || traces.length === 0) return null;

  return (
    <>
      <Typography
        variant="subtitle1"
        style={{ fontWeight: 'bold' }}
        gutterBottom
      >
        {t('Traces')}
      </Typography>
      {traces && <TraceMessage traces={traces} />}
    </>
  );
};

export default MainTraces;
