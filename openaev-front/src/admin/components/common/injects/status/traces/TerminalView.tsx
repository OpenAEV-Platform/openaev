import { Paper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import useFetchInjectExecutionResult from '../../../../../../actions/inject_status/useFetchInjectExecutionResult';
import Empty from '../../../../../../components/Empty';
import { useFormatter } from '../../../../../../components/i18n';
import { FONT_FAMILY_CODE } from '../../../../../../components/Theme';
import type { ExecutionTraceOutput, InjectTarget } from '../../../../../../utils/api-types';

interface Props {
  injectId: string;
  target: InjectTarget;
}

const TerminalView: FunctionComponent<Props> = ({ injectId, target }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { injectExecutionResult, loading } = useFetchInjectExecutionResult(injectId, target);

  if (!injectExecutionResult || injectExecutionResult?.execution_traces.length === 0) {
    return <Empty message={t('No traces on this target.')} />;
  }

  const firstExec = injectExecutionResult?.execution_traces[0].execution_time;
  const parseTraces = (tr: ExecutionTraceOutput) => {
    const parsed = JSON.parse(tr.execution_message);
    const stdout = parsed.stdout || '';
    const stderr = parsed.stderr || '';
    return [stdout, stderr];
  };
  const isDark = theme.palette.mode === 'dark';

  return (
    <>
      {!loading && (
        <Paper variant="outlined" style={{ padding: theme.spacing(2) }}>
          <div
            style={{
              background: isDark ? theme.palette.common.black : theme.palette.common.white,
              color: isDark ? theme.palette.common.white : theme.palette.common.black,
              fontFamily: FONT_FAMILY_CODE,
              padding: theme.spacing(2),
              borderRadius: theme.spacing(1),
              whiteSpace: 'pre-wrap',
              fontSize: theme.typography.h4.fontSize,
              overflowX: 'auto',
              maxHeight: '400px',
            }}
          >
            {`${firstExec} ${injectExecutionResult?.payload_command_blocks.map(p => p.command_content).join(' ')} \n`}
            {injectExecutionResult?.execution_traces.map((tr) => {
              const [stdout, stderr] = parseTraces(tr);

              return (
                <>
                  {tr.execution_time + ' '}
                  {stdout && (<span>{stdout}</span>)}
                  {stderr && (<span style={{ color: theme.palette.error.main }}>{stderr}</span>)}
                </>
              );
            })}
          </div>
        </Paper>
      )}
    </>
  );
}
;

export default TerminalView;
