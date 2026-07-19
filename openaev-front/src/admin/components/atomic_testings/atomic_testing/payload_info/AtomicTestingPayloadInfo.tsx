import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchAtomicTestingPayload } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { type StatusPayloadOutput } from '../../../../../utils/api-types';
import CommandsInfoCard from './CommandsInfoCard';
import OutputParserInfoCard from './OutputParserInfoCard';
import PayloadInfoPaper from './PayloadInfoPaper';

const AtomicTestingPayloadInfo: FunctionComponent = () => {
  const theme = useTheme();
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
    <div
      id="atomic-testing-info"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
    >
      <PayloadInfoPaper payloadOutput={payloadOutput} />
      <CommandsInfoCard payloadOutput={payloadOutput} />
      <OutputParserInfoCard outputParsers={payloadOutput?.payload_output_parsers || []} />
    </div>
  );
};

export default AtomicTestingPayloadInfo;
