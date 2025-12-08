import { Paper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchConnectorInstanceLogs } from '../../../../actions/connector_instances/connector-instance-actions';
import { useFormatter } from '../../../../components/i18n';
import { type ConnectorInstanceLog } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

const useStyles = makeStyles()(theme => ({
  paper: {
    padding: theme.spacing(2),
    margin: theme.spacing(2),
  },
}));

type ConnectorLogsProps = { connectorInstanceId: string };
type ConnectorInstanceLogResponse = { data: ConnectorInstanceLog[] };

const ConnectorLogs = ({ connectorInstanceId }: ConnectorLogsProps) => {
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();
  const isDark = theme.palette.mode === 'dark';

  const [logs, setLogs] = useState<ConnectorInstanceLog[]>([]);

  useDataLoader(() => {
    if (connectorInstanceId) {
      dispatch(fetchConnectorInstanceLogs(connectorInstanceId))
        .then((result: ConnectorInstanceLogResponse) =>
          setLogs(result.data),
        );
    }
  }, [connectorInstanceId]);

  return (
    <Paper variant="outlined" className={classes.paper}>
      <div
        style={{
          background: isDark ? theme.palette.common.black : theme.palette.common.white,
          color: isDark ? theme.palette.common.white : theme.palette.common.black,
          // fontFamily: FONT_FAMILY_CODE, : TODO : PR ROMU TERMINAL VIEW
          padding: theme.spacing(2),
          borderRadius: theme.spacing(1),
          whiteSpace: 'pre-wrap',
          fontSize: theme.typography.h4.fontSize,
          overflowX: 'auto',
          maxHeight: '400px',
        }}
      >
        {logs.length > 0 ? logs.map(log => (
          <div key={log.connector_instance_log_id}>
            {log.connector_instance_log}
          </div>
        )) : <div>{t('No log for the moment.')}</div>}
      </div>
    </Paper>
  );
};
export default ConnectorLogs;
