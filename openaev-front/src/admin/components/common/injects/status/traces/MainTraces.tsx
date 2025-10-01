import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../../../components/i18n';
import { type ExecutionTraceOutput } from '../../../../../../utils/api-types';
import TraceMessage from './TraceMessage';

interface Props { traces?: ExecutionTraceOutput[] }

const MainTraces = ({ traces }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  if (!traces || traces.length === 0) return null;

  return (
    <>
      <Typography
        variant="subtitle1"
        style={{
          fontWeight: 'bold',
          marginTop: theme.spacing(3),
        }}
        gutterBottom
      >
        {t('Traces')}
      </Typography>
      {traces && <TraceMessage traces={traces} />}
    </>
  );
};

export default MainTraces;
