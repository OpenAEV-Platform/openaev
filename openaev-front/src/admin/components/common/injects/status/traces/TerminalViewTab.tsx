import { Paper } from '@mui/material';
import { type FunctionComponent, useMemo } from 'react';

import useFetchInjectExecutionResult from '../../../../../../actions/inject_status/useFetchInjectExecutionResult';
import type { InjectTarget } from '../../../../../../utils/api-types';
import TerminalView from './TerminalView';

interface Props {
  injectId: string;
  target: InjectTarget;
  forceExpanded: boolean;
}

const TerminalViewTab: FunctionComponent<Props> = ({ injectId, target, forceExpanded }) => {
  const { injectExecutionResult, loading } = useFetchInjectExecutionResult(injectId, target);

  const nonEmptyTraces = useMemo(() => {
    if (!injectExecutionResult?.execution_traces) {
      return [];
    }

    return Object.entries(injectExecutionResult.execution_traces)
      .filter(([, traces]) => traces.length > 0);
  }, [injectExecutionResult]);

  if (loading || nonEmptyTraces.length === 0) {
    return null;
  }

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
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
