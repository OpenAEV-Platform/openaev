import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useMemo } from 'react';

import ExpandableSection from '../../../../../../components/common/ExpandableSection';
import Terminal, { type TerminalLine } from '../../../../../../components/common/terminal/Terminal';
import { type ExecutionTraceOutput, type PayloadCommandBlock } from '../../../../../../utils/api-types';

interface Props {
  payloadCommandBlocks: PayloadCommandBlock[];
  traces: ExecutionTraceOutput[];
  forceExpanded: boolean;
}

const TerminalView: FunctionComponent<Props> = ({ payloadCommandBlocks, traces, forceExpanded }) => {
  const theme = useTheme();

  const firstTrace = traces[0];

  const parseTraceOutput = useCallback((trace: ExecutionTraceOutput) => {
    try {
      const parsed = JSON.parse(trace.execution_message);
      return {
        stdout: parsed.stdout ?? '',
        stderr: parsed.stderr ?? '',
      };
    } catch {
      return {
        stdout: trace.execution_message,
        stderr: '',
      };
    }
  }, []);

  const commandLine = useMemo(() => {
    if (!firstTrace) return '';

    const commands = payloadCommandBlocks
      .map(p => p.command_content)
      .join(' ');

    return `${firstTrace.execution_time} ${commands}\n`;
  }, [firstTrace, payloadCommandBlocks]);

  const lines: TerminalLine[] = useMemo(() => {
    if (!firstTrace) return [];

    return [
      {
        key: 'command',
        date: firstTrace.execution_time,
        content: commandLine,
      },
      ...traces.flatMap((trace) => {
        const { stdout, stderr } = parseTraceOutput(trace);
        const result: TerminalLine[] = [];

        if (stdout) {
          result.push({
            key: `${trace.execution_time}-stdout`,
            date: trace.execution_time,
            content: stdout,
          });
        }

        if (stderr) {
          result.push({
            key: `${trace.execution_time}-stderr`,
            date: trace.execution_time,
            content: stderr,
            level: 'error',
          });
        }

        return result;
      }),
    ];
  }, [traces, parseTraceOutput, commandLine, firstTrace]);

  const header = (
    <Typography gutterBottom sx={{ mr: theme.spacing(1.5) }}>
      {firstTrace?.execution_agent?.agent_executed_by_user}
    </Typography>
  );

  if (!firstTrace) {
    return null;
  }

  return (
    <ExpandableSection
      forceExpanded={forceExpanded}
      header={header}
    >
      <Terminal
        maxHeight={400}
        lines={lines}
      />
    </ExpandableSection>
  );
}
;

export default TerminalView;
