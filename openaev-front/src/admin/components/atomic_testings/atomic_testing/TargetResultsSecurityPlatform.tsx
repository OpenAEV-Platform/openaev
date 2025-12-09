import { OpenInNew } from '@mui/icons-material';
import { Link, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchExpectationTraces } from '../../../../actions/atomic_testings/atomic-testing-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { InjectExpectationResult, InjectExpectationTrace } from '../../../../utils/api-types';
import { type InjectExpectationsStore } from '../../common/injects/expectations/Expectation';

const useStyles = makeStyles()(() => ({ flexContainer: { display: 'flex' } }));

interface Props {
  injectExpectation: InjectExpectationsStore;
  sourceId: string;
  expectationResult: InjectExpectationResult | null;
  open: boolean;
  handleClose: () => void;
}

const TargetResultsSecurityPlatform: FunctionComponent<Props> = ({
  injectExpectation,
  sourceId,
  expectationResult,
  handleClose,
  open,
}) => {
  const { classes } = useStyles();
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const [expectationTraces, setExpectationTraces] = useState<InjectExpectationTrace[]>([]);

  useEffect(() => {
    fetchExpectationTraces(injectExpectation.inject_expectation_id, sourceId).then((result: { data: InjectExpectationTrace[] }) => setExpectationTraces(result.data ?? []));
  }, [injectExpectation.inject_expectation_id, sourceId]);

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={expectationResult ? t(expectationResult.sourceName || '-') : '-'}
    >
      <>
        <Typography variant="body1">
          {`${injectExpectation.inject_expectation_type} ${t('Alerts')}`}
        </Typography>
        <TableContainer sx={{ marginTop: theme.spacing(4) }}>
          <Table
            sx={{ minWidth: 650 }}
            size="small"
          >
            <TableHead>
              <TableRow sx={{ textTransform: 'uppercase' }}>
                <TableCell>{t('Name')}</TableCell>
                <TableCell>{`${injectExpectation.inject_expectation_type} ${t('Date')}`}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {
                expectationTraces.map((expectationTrace: InjectExpectationTrace) => {
                  return (
                    <TableRow
                      key={expectationTrace.inject_expectation_trace_id}
                      sx={{ height: '50px' }}
                    >
                      <TableCell sx={{ fontSize: '14px' }}>
                        <Link underline="always" href={expectationTrace.inject_expectation_trace_alert_link} target="_blank">
                          <div className={classes.flexContainer}>
                            <div>
                              {expectationTrace.inject_expectation_trace_alert_name}
                            </div>
                            <div style={{
                              paddingTop: '2px',
                              marginLeft: '2px',
                            }}
                            >
                              <OpenInNew fontSize="inherit" />
                            </div>
                          </div>
                        </Link>
                      </TableCell>
                      <TableCell sx={{ fontSize: '14px' }}>
                        {fldt(expectationTrace.inject_expectation_trace_date)}
                      </TableCell>
                    </TableRow>
                  );
                })
              }
            </TableBody>
          </Table>
        </TableContainer>
      </>

    </Drawer>

  );
};

export default TargetResultsSecurityPlatform;
