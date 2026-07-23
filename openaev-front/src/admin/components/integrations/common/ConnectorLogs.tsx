import { Paper, TablePagination } from '@mui/material';
import { useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchConnectorInstanceLogs } from '../../../../actions/connector_instances/connector-instance-actions';
import { type Page } from '../../../../components/common/queryable/Page';
import { ROWS_PER_PAGE_OPTIONS } from '../../../../components/common/queryable/pagination/usePaginationState';
import Terminal from '../../../../components/common/terminal/Terminal';
import { useFormatter } from '../../../../components/i18n';
import { type ConnectorInstanceLog } from '../../../../utils/api-types';

const useStyles = makeStyles()(theme => ({ paper: { padding: theme.spacing(2) } }));

type ConnectorLogsProps = { connectorInstanceId: string };

const ConnectorLogs = ({ connectorInstanceId }: ConnectorLogsProps) => {
  const { classes } = useStyles();
  const { t, fldt } = useFormatter();

  const [logs, setLogs] = useState<ConnectorInstanceLog[]>([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(ROWS_PER_PAGE_OPTIONS[0]);
  const [totalElements, setTotalElements] = useState(0);

  // Restart from the first page when switching to another connector instance.
  useEffect(() => {
    setPage(0);
  }, [connectorInstanceId]);

  useEffect(() => {
    if (connectorInstanceId) {
      searchConnectorInstanceLogs(connectorInstanceId, {
        page,
        size,
      })
        .then((result: { data: Page<ConnectorInstanceLog> }) => {
          setLogs(result.data.content);
          setTotalElements(result.data.totalElements);
        });
    }
  }, [connectorInstanceId, page, size]);

  return (
    <Paper variant="outlined" className={classes.paper}>
      <TablePagination
        component="div"
        rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
        count={totalElements}
        page={page}
        onPageChange={(_, newPage) => setPage(newPage)}
        rowsPerPage={size}
        onRowsPerPageChange={(e) => {
          setSize(parseInt(e.target.value, 10));
          setPage(0);
        }}
      />
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
