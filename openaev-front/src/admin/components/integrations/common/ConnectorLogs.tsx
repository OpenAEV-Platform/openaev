import { Paper } from '@mui/material';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchConnectorInstanceLogs } from '../../../../actions/connector_instances/connector-instance-actions';
import Terminal from '../../../../components/common/terminal/Terminal';
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
  const { classes } = useStyles();
  const { t, fldt } = useFormatter();

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
      {logs.length > 0 ? (
        <Terminal
          maxHeight={400}
          lines={logs.map(log => ({
            key: log.connector_instance_log_id,
            date: `[${fldt(log.connector_instance_log_created_at)}]`,
            content: log.connector_instance_log,
          }))}
        />
      ) : (
        <div>{t('No log for the moment.')}</div>
      )}
    </Paper>
  );
};
export default ConnectorLogs;
