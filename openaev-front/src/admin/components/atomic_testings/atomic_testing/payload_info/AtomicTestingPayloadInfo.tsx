import { Typography } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchAtomicTestingPayload } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { useFormatter } from '../../../../../components/i18n';
import { type StatusPayloadOutput } from '../../../../../utils/api-types';
import CommandsInfoCard from './CommandsInfoCard';
import OutputParserInfoCard from './OutputParserInfoCard';
import PayloadInfoPaper from './PayloadInfoPaper';

const useStyles = makeStyles()(theme => ({ payloadInfoTabContainer: { '& > div:nth-child(even)': { marginBottom: theme.spacing(2) } } }));

const AtomicTestingPayloadInfo: FunctionComponent = () => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { injectId } = useParams();
  const [payloadOutput, setPayloadOutput] = useState<StatusPayloadOutput>();

  // Fetching data
  useEffect(() => {
    if (injectId) {
      fetchAtomicTestingPayload(injectId).then((result: { data: StatusPayloadOutput }) => {
        setPayloadOutput(result.data);
      });
    }
  }, [injectId]);

  return (
    <div className={classes.payloadInfoTabContainer} id="atomic-testing-info">
      <Typography variant="h4">{t('Payload')}</Typography>
      <PayloadInfoPaper payloadOutput={payloadOutput} />
      <Typography variant="h4">{t('Commands')}</Typography>
      <CommandsInfoCard payloadOutput={payloadOutput} />
      <Typography variant="h4">{t('Output parser')}</Typography>
      <OutputParserInfoCard outputParsers={payloadOutput?.payload_output_parsers || []} />
    </div>
  );
};

export default AtomicTestingPayloadInfo;
