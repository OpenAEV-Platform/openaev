import { type ExpectationResultsByType } from '../../../utils/api-types';

// Illustrative posture used by the scenario / simulation overviews when no run
// has produced results yet, so the page previews the exact insights a real run
// yields (greyed "Sample" preview) instead of an empty placeholder. The
// distribution labels map through getStatusColor to success/failed/pending.
const SAMPLE_POSTURE: ExpectationResultsByType[] = [
  {
    type: 'PREVENTION',
    avgResult: 'FAILED',
    distribution: [
      {
        id: 'SUCCESS',
        label: 'Prevented',
        value: 34,
      },
      {
        id: 'FAILED',
        label: 'Not Prevented',
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
    avgResult: 'FAILED',
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
