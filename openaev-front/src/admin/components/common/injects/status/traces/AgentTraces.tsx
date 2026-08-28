import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../../../components/i18n';
import { type ExecutionTraceOutput } from '../../../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../../../utils/url-helper';
import AgentStatusHeader from './AgentStatusHeader';
import ExecutionTime from './ExecutionTime';
import { severityColor, severityForStatus } from './severity';
import TraceMessage from './TraceMessage';
import useAgentStatus from './useAgentStatus';

interface Props { traces: ExecutionTraceOutput[] }

// A single agent's execution traces, rendered as a static titled card (no
// accordion): the per-agent header row is always visible with its traces
// underneath, so a single agent needs no click to reveal its output.
const AgentTraces = ({ traces }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const agentStatus = useAgentStatus(traces);

  return (
    <Box
      sx={{
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        borderRadius: 1,
        marginBottom: 1,
        backgroundColor: alpha(theme.palette.background.paper, 0.4),
        overflow: 'hidden',
      }}
    >
      <Box sx={{
        paddingBlock: 1,
        paddingInline: 1.5,
      }}
      >
        <AgentStatusHeader agentName={agentStatus.agentName} statusName={agentStatus.statusName} />
      </Box>
      <Box
        sx={{
          paddingBlock: 1,
          paddingInline: 1.5,
          borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
        }}
      >
        <ExecutionTime
          startDate={agentStatus.trackingStart}
          endDate={agentStatus.trackingEnd}
        />
        {agentStatus.executorType && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1),
              padding: theme.spacing(0.5, 0),
            }}
          >
            <Typography
              variant="caption"
              sx={{
                color: 'text.secondary',
                textTransform: 'uppercase',
                letterSpacing: 0.5,
              }}
            >
              {t('Executor')}
            </Typography>
            <img
              src={buildTenantApiPath(`/api/images/executors/icons/${agentStatus.executorType}`)}
              alt={agentStatus.executorType}
              style={{
                width: 20,
                height: 20,
                borderRadius: 4,
              }}
            />
          </div>
        )}
        <Typography
          variant="caption"
          sx={{
            color: 'text.secondary',
            textTransform: 'uppercase',
            letterSpacing: 0.5,
            display: 'block',
            marginTop: theme.spacing(0.5),
            marginBottom: theme.spacing(1),
          }}
        >
          {t('Traces')}
        </Typography>
        {/* Vertical timeline: one node per action group (Start, Attack command,
            Cleanup command, Last trace). A single rail connects the colored
            points; the trace rows render borderless so the rail carries the
            structure instead of a bordered box per line. */}
        {agentStatus.tracesByAction.map((group, index) => {
          const isFirst = index === 0;
          const isLast = index === agentStatus.tracesByAction.length - 1;
          // The node color reflects the group's final trace status.
          const groupStatus = group.traces.at(-1)?.execution_status;
          const dotColor = severityColor(theme, severityForStatus(groupStatus));
          return (
            <div
              key={`trace-group-${index}`}
              style={{
                display: 'flex',
                gap: theme.spacing(1.5),
              }}
            >
              {/* Rail + point */}
              <div style={{
                position: 'relative',
                width: 12,
                flexShrink: 0,
              }}
              >
                <div
                  style={{
                    position: 'absolute',
                    left: 5,
                    width: 2,
                    top: isFirst ? 10 : 0,
                    bottom: isLast ? undefined : 0,
                    height: isLast ? 10 : undefined,
                    backgroundColor: alpha(theme.palette.text.primary, 0.12),
                  }}
                />
                <div
                  style={{
                    position: 'absolute',
                    left: 0,
                    top: 4,
                    width: 12,
                    height: 12,
                    borderRadius: '50%',
                    backgroundColor: dotColor,
                    border: `2px solid ${theme.palette.background.paper}`,
                    boxShadow: `0 0 0 1px ${alpha(dotColor, 0.4)}`,
                  }}
                />
              </div>
              {/* Group content */}
              <div style={{
                flex: 1,
                minWidth: 0,
                paddingBottom: isLast ? 0 : theme.spacing(1.5),
              }}
              >
                <Typography variant="caption" sx={{ fontWeight: 600 }}>
                  {t(group.action)}
                </Typography>
                <TraceMessage traces={group.traces} variant="plain" />
              </div>
            </div>
          );
        })}
      </Box>
    </Box>
  );
};

export default AgentTraces;
