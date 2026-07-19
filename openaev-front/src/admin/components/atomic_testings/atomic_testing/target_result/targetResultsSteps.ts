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
  lastExecutionStartDate: string;
  lastExecutionEndDate: string;
  /** Translated label for the "Attack started" step. */
  startLabel: string;
  /** Translated label for the "Attack ended" step. */
  endLabel: string;
}

const isEmptyExpectationLabel = (label: string) => label === '' || label.startsWith('No Expectation');

export const computeTimelineSteps = ({
  targetResultsByType,
  injectStatusName,
  lastExecutionStartDate,
  lastExecutionEndDate,
  startLabel,
  endLabel,
}: ComputeStepsInput): TimelineStep[] => {
  // Same status derivation as the former computeInitialSteps.
  let startStatus = 'PENDING';
  if (injectStatusName === 'QUEUING') {
    startStatus = 'QUEUING';
  } else if (lastExecutionStartDate || lastExecutionEndDate) {
    startStatus = 'SUCCESS';
  }
  const endStatus = lastExecutionEndDate ? 'SUCCESS' : 'PENDING';

  const initialSteps: TimelineStep[] = [
    {
      key: 'attack-started',
      label: startLabel,
      status: startStatus,
      timestamp: lastExecutionStartDate || undefined,
    },
    {
      key: 'attack-ended',
      label: endLabel,
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
