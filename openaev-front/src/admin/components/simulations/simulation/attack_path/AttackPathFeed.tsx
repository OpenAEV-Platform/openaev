import { Box, Chip, Collapse, IconButton, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { ExpandMore as ExpandMoreIcon, Terminal as TerminalIcon } from '@mui/icons-material';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import type { InjectExpectation, InjectStatus } from '../../../../../utils/api-types';
import type { WorkflowStep } from '../../../../../utils/api-types-custom';
import { isActionStep, getStepLabel } from '../../../scenarios/scenario/logic/logicUtils';
import { STATUS_COLORS, resolveStepStatus, type AttackStepStatus } from './AttackPathFlow';

interface AttackPathFeedProps {
  steps: WorkflowStep[];
  expectations: InjectExpectation[];
  injectStatuses: Record<string, InjectStatus>;
  selectedStepId: string | null;
  onSelectStep: (stepId: string | null) => void;
}

const StatusDot: FunctionComponent<{ status: AttackStepStatus }> = ({ status }) => (
  <Box
    sx={{
      width: 10,
      height: 10,
      borderRadius: '50%',
      backgroundColor: STATUS_COLORS[status].fill,
      flexShrink: 0,
    }}
  />
);

const AttackPathFeed: FunctionComponent<AttackPathFeedProps> = ({
  steps,
  expectations,
  injectStatuses,
  selectedStepId,
  onSelectStep,
}) => {
  const theme = useTheme();
  const { t, fldt } = useFormatter();
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // Only show action steps, sorted most recent first by creation date
  const actionSteps = steps
    .filter(isActionStep)
    .sort((a, b) => {
      const dateA = a.step_created_at ?? '';
      const dateB = b.step_created_at ?? '';
      return dateB.localeCompare(dateA);
    });

  return (
    <Box
      sx={{
        width: 350,
        minWidth: 350,
        height: '100%',
        overflow: 'auto',
        borderRight: `1px solid ${theme.palette.divider}`,
        padding: 1.5,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
      }}
    >
      <Typography variant="subtitle2" sx={{ px: 0.5, pb: 0.5, color: theme.palette.text.secondary }}>
        {t('Execution feed')}
      </Typography>

      {actionSteps.length === 0 && (
        <Typography variant="body2" sx={{ color: theme.palette.text.secondary, textAlign: 'center', py: 4 }}>
          {t('No injects executed yet')}
        </Typography>
      )}

      {actionSteps.map((step) => {
        const data = JSON.parse(step.step_data ?? '{}');
        const injectId = data.inject_id;
        const status = resolveStepStatus(expectations, injectId);
        const isSelected = selectedStepId === step.step_id;
        const isExpanded = expandedId === step.step_id;
        const injectStatus = injectId ? injectStatuses[injectId] : undefined;

        return (
          <Paper
            key={step.step_id}
            elevation={isSelected ? 4 : 1}
            onClick={() => onSelectStep(isSelected ? null : step.step_id)}
            sx={{
              p: 1.5,
              cursor: 'pointer',
              border: isSelected ? `2px solid ${STATUS_COLORS[status].fill}` : '2px solid transparent',
              transition: 'border 0.2s, box-shadow 0.2s',
              '&:hover': { boxShadow: theme.shadows[3] },
            }}
          >
            {/* Header row */}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <StatusDot status={status} />
              <Typography variant="body2" sx={{ fontWeight: 600, flex: 1, minWidth: 0 }} noWrap>
                {getStepLabel(step)}
              </Typography>
              {data.inject_kill_chain_phases?.[0]?.phase_name && (
                <Chip
                  label={data.inject_kill_chain_phases[0].phase_name}
                  size="small"
                  sx={{ fontSize: 10, height: 20 }}
                />
              )}
            </Box>

            {/* Description */}
            {data.inject_description && (
              <Typography
                variant="caption"
                sx={{
                  color: theme.palette.text.secondary,
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                  mt: 0.5,
                }}
              >
                {data.inject_description}
              </Typography>
            )}

            {/* Status row */}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5 }}>
              <Typography variant="caption" sx={{ color: theme.palette.text.secondary, fontSize: 10 }}>
                {status === 'prevented' && t('Prevented')}
                {status === 'detected' && t('Detected')}
                {status === 'undetected' && t('Not Detected')}
                {status === 'pending' && t('Pending')}
              </Typography>
              <Box sx={{ flex: 1 }} />
              {injectStatus && (
                <Typography variant="caption" sx={{ color: theme.palette.text.secondary, fontSize: 10 }}>
                  {fldt(injectStatus.tracking_sent_date)}
                </Typography>
              )}
            </Box>

            {/* Terminal toggle */}
            <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5 }}>
              <IconButton
                size="small"
                onClick={(e) => {
                  e.stopPropagation();
                  setExpandedId(isExpanded ? null : step.step_id);
                }}
                sx={{ p: 0.25 }}
              >
                <TerminalIcon sx={{ fontSize: 16 }} />
                <ExpandMoreIcon
                  sx={{
                    fontSize: 14,
                    transform: isExpanded ? 'rotate(180deg)' : 'none',
                    transition: 'transform 0.2s',
                  }}
                />
              </IconButton>
            </Box>

            {/* Terminal output */}
            <Collapse in={isExpanded}>
              <Box
                sx={{
                  mt: 1,
                  p: 1,
                  backgroundColor: '#1e1e1e',
                  borderRadius: 1,
                  maxHeight: 200,
                  overflow: 'auto',
                  fontFamily: 'monospace',
                  fontSize: 11,
                  color: '#d4d4d4',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                }}
              >
                {injectStatus
                  ? (injectStatus as Record<string, unknown>).status_traces
                    ? JSON.stringify((injectStatus as Record<string, unknown>).status_traces, null, 2)
                    : `Status: ${injectStatus.status_name ?? 'N/A'}`
                  : t('No execution data')}
              </Box>
            </Collapse>
          </Paper>
        );
      })}
    </Box>
  );
};

export default AttackPathFeed;
