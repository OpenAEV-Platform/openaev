import { AssignmentTurnedIn, BugReportOutlined, PublishedWithChangesOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { NewspaperVariantMultipleOutline } from 'mdi-material-ui';

import { ExpectationType } from './Expectation';

export const FAILED = 'Failed';

export const HUMAN_EXPECTATION = ['MANUAL', 'CHALLENGE', 'ARTICLE'];

export const isAutomatic = (type: string) => {
  return [ExpectationType.ARTICLE.toString(), ExpectationType.PREVENTION.toString(), ExpectationType.DETECTION.toString(), ExpectationType.VULNERABILITY.toString()].includes(type);
};

export const typeIcon = (type: string) => {
  if (type === 'DETECTION') {
    return <TrackChangesOutlined />;
  }
  if (type === 'PREVENTION') {
    return <PublishedWithChangesOutlined />;
  }
  if (type === 'ARTICLE') {
    return <NewspaperVariantMultipleOutline />;
  }
  if (type === 'VULNERABILITY') {
    return <BugReportOutlined />;
  }
  return <AssignmentTurnedIn />;
};

export const isTechnicalExpectation = (type: string) => {
  return [ExpectationType.PREVENTION.toString(), ExpectationType.DETECTION.toString(), ExpectationType.VULNERABILITY.toString()].includes(type);
};
export const isManualExpectation = (type: string) => {
  return [ExpectationType.MANUAL.toString(), ExpectationType.ARTICLE.toString(), ExpectationType.CHALLENGE.toString()].includes(type);
};
