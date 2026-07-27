import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';

export interface TimelineStep {
  key: string;
  label: string;
  /** Expectation type (PREVENTION, DETECTION, ...) - undefined for the attack start / end steps. */
  type?: string;
  status: string;
  timestamp?: string;
}

// Pure step-computation logic extracted verbatim from the former
// TargetResultsReactFlow component (behavior must stay identical).

export const getStatusLabel = (type: string, status: string[]): string => {
  switch (type) {
    case 'DETECTION':
      if (status.includes('UNKNOWN')) {
        return 'No Expectation for Detection';
      }
      if (status.includes('PENDING')) {
        return 'Waiting for Detection';
      }
      return status.every(s => s === 'SUCCESS') ? 'Attack Detected' : 'Attack Not Detected';
    case 'MANUAL':
    case 'ARTICLE':
    case 'CHALLENGE':
      if (status.includes('UNKNOWN')) {
        return 'No Expectation for Manual';
      }
      if (status.includes('PENDING')) {
        return 'Waiting for Validation';
      }
      return status.every(s => s === 'SUCCESS') ? 'Validation Success' : 'Validation Failed';
    case 'PREVENTION':
      if (status.includes('UNKNOWN')) {
        return 'No Expectation';
      }
      if (status.includes('PENDING')) {
        return 'Waiting for Prevention';
      }
      return status.every(s => s === 'SUCCESS') ? 'Attack Prevented' : 'Attack Not Prevented';
    default:
      return '';
  }
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
