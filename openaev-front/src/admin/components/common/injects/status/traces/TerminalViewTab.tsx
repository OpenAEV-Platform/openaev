import { type FunctionComponent, useMemo } from 'react';

import useFetchInjectExecutionResult from '../../../../../../actions/inject_status/useFetchInjectExecutionResult';
import Empty from '../../../../../../components/Empty';
import { useFormatter } from '../../../../../../components/i18n';
import type { InjectTarget } from '../../../../../../utils/api-types';
import TerminalView from './TerminalView';

interface Props {
  injectId: string;
  target: InjectTarget;
}

const TerminalViewTab: FunctionComponent<Props> = ({ injectId, target }) => {
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

  // No wrapping card: the section header delimits the block and each terminal
  // renders its own strong dark panel, so an extra outline would just add noise.
  return (
    <>
      {nonEmptyTraces.map(([key, value]) => (
        <TerminalView
          key={key}
          payloadCommandBlocks={injectExecutionResult?.payload_command_blocks ?? []}
          traces={value}
        />
      ))}
    </>
  );
};

export default TerminalViewTab;
