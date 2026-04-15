import { Paper } from '@mui/material';
import { type FunctionComponent, useMemo } from 'react';

import useFetchInjectExecutionResult from '../../../../../../actions/inject_status/useFetchInjectExecutionResult';
import Empty from '../../../../../../components/Empty';
import { useFormatter } from '../../../../../../components/i18n';
import type { InjectTarget } from '../../../../../../utils/api-types';
import TerminalView from './TerminalView';

interface Props {
  injectId: string;
  target: InjectTarget;
  forceExpanded: boolean;
}

const TerminalViewTab: FunctionComponent<Props> = ({ injectId, target, forceExpanded }) => {
  const { t } = useFormatter();
  const { injectExecutionResult, loading } = useFetchInjectExecutionResult(injectId, target);

  const nonEmptyTraces = useMemo(() => {
    if (!injectExecutionResult?.execution_traces) {
      return [];
    }

    return Object.entries(injectExecutionResult.execution_traces)
      .filter(([, traces]) => traces.length > 0);
  }, [injectExecutionResult]);

  if (loading || nonEmptyTraces.length === 0) {
    return <Empty message={t('No traces on this target.')} />;
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      {nonEmptyTraces.map(([key, value]) => (
        <TerminalView
          key={key}
          payloadCommandBlocks={injectExecutionResult?.payload_command_blocks ?? []}
          traces={value}
          forceExpanded={forceExpanded}
        />
      ))}
    </Paper>
  );
};

export default TerminalViewTab;
