import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useMemo } from 'react';

import ExpandableSection from '../../../../../../components/common/ExpandableSection';
import { FONT_FAMILY_CODE } from '../../../../../../components/Theme';
import { type ExecutionTraceOutput, type PayloadCommandBlock } from '../../../../../../utils/api-types';

interface Props {
  payloadCommandBlocks: PayloadCommandBlock[];
  traces: ExecutionTraceOutput[];
  forceExpanded: boolean;
}

const TerminalView: FunctionComponent<Props> = ({ payloadCommandBlocks, traces, forceExpanded }) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

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
        {commandLine}
        {traces.map((trace) => {
          const { stdout, stderr } = parseTraceOutput(trace);

          return (
            <Box key={trace.execution_time}>
              {trace.execution_time + ' '}
              {stdout && <span>{stdout}</span>}
              {stderr && <span style={{ color: theme.palette.error.main }}>{stderr}</span>}
            </Box>
          );
        })}
      </div>
    </ExpandableSection>
  );
}
;

export default TerminalView;
