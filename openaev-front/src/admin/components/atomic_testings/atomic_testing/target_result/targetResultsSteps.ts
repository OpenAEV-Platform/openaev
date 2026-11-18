import { HUMAN_EXPECTATION_TYPES, type InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';

export interface TimelineStep {
  key: string;
  /** Status-derived label (e.g. "Validation Failed") - used as a fallback when the expectation has no name. */
  label: string;
  /** The expectation's own name (e.g. "Credentials not submitted") - preferred over the status label in the timeline. */
  name?: string;
  /** Expectation type (PREVENTION, DETECTION, ...) - undefined for the attack start / end steps. */
  type?: string;
  status: string;
  timestamp?: string;
}

// Pure step-computation logic extracted verbatim from the former
// TargetResultsReactFlow component (behavior must stay identical).

interface StepLabels {
  unknown: string;
  pending: string;
  success: string;
  failed: string;
}

const STEP_LABELS: Record<string, StepLabels> = {
  DETECTION: {
    unknown: 'No Expectation for Detection',
    pending: 'Waiting for Detection',
    success: 'Attack Detected',
    failed: 'Attack Not Detected',
  },
  PREVENTION: {
    unknown: 'No Expectation',
    pending: 'Waiting for Prevention',
    success: 'Attack Prevented',
    failed: 'Attack Not Prevented',
  },
};

const HUMAN_STEP_LABELS: StepLabels = {
  unknown: 'No Expectation for Manual',
  pending: 'Waiting for Validation',
  success: 'Validation Success',
  failed: 'Validation Failed',
};

// Empty for a type without step labels: the step is then left out of the timeline.
export const getStatusLabel = (type: string, status: string[]): string => {
  const labels = STEP_LABELS[type] ?? (HUMAN_EXPECTATION_TYPES.includes(type) ? HUMAN_STEP_LABELS : undefined);
  if (!labels) {
    return '';
  }
  if (status.includes('UNKNOWN')) {
    return labels.unknown;
  }
  if (status.includes('PENDING')) {
    return labels.pending;
  }
  return status.every(s => s === 'SUCCESS') ? labels.success : labels.failed;
};

export const getStatus = (status: string[]): string => {
  if (status.includes('UNKNOWN')) {
    return 'UNKNOWN';
  }
  if (status.includes('PENDING')) {
    return 'PENDING';
  }
  if (status.includes('PARTIAL')) {
    return 'PARTIAL';
  }
  if (status.includes('FAILED')) {
    return 'FAILED';
  }
  return status.every(s => s === 'SUCCESS') ? 'SUCCESS' : 'FAILED';
};

interface ComputeStepsInput {
  targetResultsByType: Record<string, InjectExpectationsStore[]>;
  injectStatusName?: string;
  /** Per-target execution outcome (SUCCESS | FAILED | PARTIAL | PENDING | UNKNOWN). */
  targetExecutionStatus?: string;
  lastExecutionStartDate: string;
  lastExecutionEndDate: string;
  /** Translated label for the "Attack started" step. */
  startLabel: string;
  /** Translated label for the "Attack ended" step (success / pending). */
  endLabel: string;
  /** Translated label for the "Attack ended" step when it failed. */
  endFailedLabel: string;
}

const isEmptyExpectationLabel = (label: string) => label === '' || label.startsWith('No Expectation');

// Resolve the "Attack ended" step status from the real outcome instead of
// turning green whenever an end date exists. The per-target execution status is
// authoritative; when it is absent/unknown (e.g. a pre-execution failure that
// never reached the target) we fall back to the inject-level status.
const resolveEndStatus = (
  targetExecutionStatus: string | undefined,
  injectStatusName: string | undefined,
  lastExecutionEndDate: string,
): string => {
  switch (targetExecutionStatus) {
    case 'FAILED':
      return 'FAILED';
    case 'PARTIAL':
      return 'PARTIAL';
    case 'SUCCESS':
      return 'SUCCESS';
    default:
      break;
  }
  if (!lastExecutionEndDate) {
    return 'PENDING';
  }
  if (injectStatusName === 'ERROR') {
    return 'FAILED';
  }
  if (injectStatusName === 'PARTIAL') {
    return 'PARTIAL';
  }
  return 'SUCCESS';
};

export const computeTimelineSteps = ({
  targetResultsByType,
  injectStatusName,
  targetExecutionStatus,
  lastExecutionStartDate,
  lastExecutionEndDate,
  startLabel,
  endLabel,
  endFailedLabel,
}: ComputeStepsInput): TimelineStep[] => {
  // Same status derivation as the former computeInitialSteps.
  let startStatus = 'PENDING';
  if (injectStatusName === 'QUEUING') {
    startStatus = 'QUEUING';
  } else if (lastExecutionStartDate || lastExecutionEndDate) {
    startStatus = 'SUCCESS';
  }
  const endStatus = resolveEndStatus(targetExecutionStatus, injectStatusName, lastExecutionEndDate);
  const endFailed = endStatus === 'FAILED';

  const initialSteps: TimelineStep[] = [
    {
      key: 'attack-started',
      label: startLabel,
      status: startStatus,
      timestamp: lastExecutionStartDate || undefined,
    },
    {
      key: 'attack-ended',
      label: endFailed ? endFailedLabel : endLabel,
      status: endStatus,
      timestamp: lastExecutionEndDate || undefined,
    },
  ];

  const expectationSteps: TimelineStep[] = Object.entries(targetResultsByType).flatMap(([type, expectations]) => {
    return expectations
      .map((expectation, index) => {
        const statuses = [expectation.inject_expectation_status ?? 'UNKNOWN'];
        return {
          key: `${type}-${expectation.inject_expectation_id ?? index}`,
          label: getStatusLabel(type, statuses),
          name: expectation.inject_expectation_name?.trim() || undefined,
          type,
          status: getStatus(statuses),
        };
      })
      // Steps without an actual expectation ("No Expectation ...") used to render
      // as an empty trailing node - they are omitted entirely.
      .filter(step => !isEmptyExpectationLabel(step.label));
  });

  return [...initialSteps, ...expectationSteps];
};
