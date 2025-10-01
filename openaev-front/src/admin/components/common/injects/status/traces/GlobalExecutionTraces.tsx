import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../../../components/i18n';
import { type InjectStatusOutput } from '../../../../../../utils/api-types';
import ExecutionTime from './ExecutionTime';
import MainTraces from './MainTraces';

type Props = { injectStatus: InjectStatusOutput };

const GlobalExecutionTraces = ({ injectStatus }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <>
      <Typography variant="h4">{t('Execution logs')}</Typography>
      <Paper variant="outlined" style={{ padding: theme.spacing(0, 3, 3) }}>
        <ExecutionTime
          style={{ marginTop: theme.spacing(3) }}
          startDate={injectStatus.tracking_sent_date}
          endDate={injectStatus.tracking_end_date}
        />
        <MainTraces traces={injectStatus.status_main_traces} />
      </Paper>
    </>
  );
};

export default GlobalExecutionTraces;
