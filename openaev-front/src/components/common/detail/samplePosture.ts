import { type ExpectationResultsByType } from '../../../utils/api-types';

// Illustrative posture used by the scenario / simulation overviews when no run
// has produced results yet, so the page previews the exact insights a real run
// yields (greyed "Sample" preview) instead of an empty placeholder. The
// distribution labels map through getStatusColor to success/partial/failed/pending.
const SAMPLE_POSTURE: ExpectationResultsByType[] = [
  {
    type: 'PREVENTION',
    avgResult: 'SUCCESS',
    distribution: [
      {
        id: 'PREVENTED',
        label: 'Prevented',
        value: 34,
      },
      {
        id: 'PARTIAL',
        label: 'Partially prevented',
        value: 5,
      },
      {
        id: 'FAILED',
        label: 'Failed',
        value: 9,
      },
    ],
  },
  {
    type: 'DETECTION',
    avgResult: 'SUCCESS',
    distribution: [
      {
        id: 'DETECTED',
        label: 'Detected',
        value: 41,
      },
      {
        id: 'FAILED',
        label: 'Failed',
        value: 6,
      },
    ],
  },
  {
    type: 'VULNERABILITY',
    avgResult: 'FAILED',
    distribution: [
      {
        id: 'NOT_VULNERABLE',
        label: 'Not vulnerable',
        value: 13,
      },
      {
        id: 'VULNERABLE',
        label: 'Vulnerable',
        value: 27,
      },
    ],
  },
  {
    type: 'HUMAN_RESPONSE',
    avgResult: 'PARTIAL',
    distribution: [
      {
        id: 'SUCCESS',
        label: 'Successful',
        value: 18,
      },
      {
        id: 'PENDING',
        label: 'Pending',
        value: 7,
      },
      {
        id: 'FAILED',
        label: 'Failed',
        value: 5,
      },
    ],
  },
];

export default SAMPLE_POSTURE;
