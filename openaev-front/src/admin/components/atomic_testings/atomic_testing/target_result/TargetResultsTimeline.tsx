import {
  FlagOutlined,
  ModeStandbyOutlined,
} from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent, useMemo } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { expectationTypeIcon } from '../../../common/ExpectationIconByType';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import { computeTimelineSteps, type TimelineStep } from './targetResultsSteps';

interface Props {
  targetResultsByType: Record<string, InjectExpectationsStore[]>;
  lastExecutionStartDate: string;
  injectStatusName?: string;
  targetExecutionStatus?: string;
  lastExecutionEndDate: string;
}

const TERMINAL_STATUSES = ['SUCCESS', 'SUCCESSFUL', 'FAILED', 'PARTIAL'];

const stepIcon = (step: TimelineStep) => {
  if (step.key === 'attack-started') return FlagOutlined;
  if (step.key === 'attack-ended') return ModeStandbyOutlined;
  // Expectation steps share the central icon map (HUMAN_RESPONSE covers the default case).
  return expectationTypeIcon(step.type ?? 'HUMAN_RESPONSE');
};

/**
 * Horizontal attack status timeline (Attack started -> expectations -> Attack
 * ended). Replaces the former ReactFlow-based chain with a compact flex row.
 */
const TargetResultsTimeline: FunctionComponent<Props> = ({
  targetResultsByType,
  lastExecutionStartDate,
  injectStatusName,
  targetExecutionStatus,
  lastExecutionEndDate,
}) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();

  // Same color mapping as the former getColor (QUEUING now uses the theme
  // warning tone instead of a hardcoded yellow).
  const getColor = (status: string): string => {
    switch (status) {
      case 'SUCCESS':
      case 'SUCCESSFUL':
        return theme.palette.success.main;
      case 'FAILED':
        return theme.palette.error.main;
      case 'PARTIAL':
      case 'QUEUING':
        return theme.palette.warning.main;
      case 'PENDING':
      default:
        return theme.palette.text.secondary;
    }
  };

  const steps = useMemo(() => computeTimelineSteps({
    targetResultsByType,
    injectStatusName,
    targetExecutionStatus,
    lastExecutionStartDate,
    lastExecutionEndDate,
    startLabel: t('Attack started'),
    endLabel: t('Attack ended'),
    endFailedLabel: t('Attack failed'),
  }), [targetResultsByType, injectStatusName, targetExecutionStatus, lastExecutionStartDate, lastExecutionEndDate, t]);

  if (steps.length === 0) return null;

  const isCompleted = (step: TimelineStep) => TERMINAL_STATUSES.includes(step.status);

  return (
    <div style={{
      display: 'flex',
      alignItems: 'flex-start',
      flexWrap: 'wrap',
      rowGap: theme.spacing(2),
      columnGap: theme.spacing(1),
    }}
    >
      {steps.map((step, index) => {
        const Icon = stepIcon(step);
        const color = getColor(step.status);
        const completed = isCompleted(step);
        const previous = steps[index - 1];

        return (
          <Fragment key={step.key}>
            {index > 0 && previous && (
              <Box
                aria-hidden
                sx={{
                  flex: 1,
                  minWidth: 24,
                  height: '2px',
                  borderRadius: 1,
                  marginTop: '19px',
                  backgroundColor: completed && isCompleted(previous)
                    ? alpha(color, 0.4)
                    : alpha(theme.palette.text.primary, 0.08),
                }}
              />
            )}
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: theme.spacing(0.75),
              maxWidth: 150,
              opacity: completed ? 1 : 0.6,
            }}
            >
              <Box
                sx={{
                  width: 40,
                  height: 40,
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  border: `1px solid ${alpha(color, 0.4)}`,
                  backgroundColor: alpha(color, 0.12),
                }}
              >
                <Icon sx={{
                  fontSize: 20,
                  color,
                }}
                />
              </Box>
              <Typography
                sx={{
                  fontSize: 12.5,
                  fontWeight: 600,
                  lineHeight: 1.3,
                  textAlign: 'center',
                  color: completed ? color : 'text.secondary',
                }}
              >
                {step.label}
              </Typography>
              {step.timestamp && (
                <Typography
                  variant="caption"
                  sx={{
                    fontSize: 11,
                    color: 'text.secondary',
                    fontVariantNumeric: 'tabular-nums',
                    lineHeight: 1.2,
                  }}
                >
                  {nsdt(step.timestamp)}
                </Typography>
              )}
            </div>
          </Fragment>
        );
      })}
    </div>
  );
};

export default TargetResultsTimeline;
