import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useMemo } from 'react';

import Terminal, { type TerminalLine } from '../../../../../../components/common/terminal/Terminal';
import { type ExecutionTraceOutput, type PayloadCommandBlock } from '../../../../../../utils/api-types';
import TraceStatusChip from './TraceStatusChip';
import useAgentStatus from './useAgentStatus';

interface Props {
  payloadCommandBlocks: PayloadCommandBlock[];
  traces: ExecutionTraceOutput[];
}

const TerminalView: FunctionComponent<Props> = ({ payloadCommandBlocks, traces }) => {
  const theme = useTheme();
  const agentStatus = useAgentStatus(traces);

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

  if (!firstTrace) {
    return null;
  }

  return (
    <div>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1.5),
      }}
      >
        <Typography sx={{
          fontSize: 13,
          fontWeight: 600,
        }}
        >
          {agentStatus.agentName || '-'}
        </Typography>
        {agentStatus.statusName && agentStatus.statusName !== 'Unknown' && (
          <TraceStatusChip status={agentStatus.statusName} />
        )}
      </div>
      <div style={{ margin: theme.spacing(1, 0) }}>
        <Terminal
          maxHeight={400}
          lines={lines}
        />
      </div>
    </div>
  );
};

export default TerminalView;
