import { AssignmentTurnedIn, PublishedWithChangesOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { NewspaperVariantMultipleOutline } from 'mdi-material-ui';

import { ExpectationType } from './Expectation';

export const FAILED = 'Failed';

export const isAutomatic = (type: string) => {
  return [ExpectationType.ARTICLE.toString(), ExpectationType.PREVENTION.toString(), ExpectationType.DETECTION.toString()].includes(type);
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
  return <AssignmentTurnedIn />;
};

export const isTechnicalExpectation = (type: string) => {
  return [ExpectationType.PREVENTION.toString(), ExpectationType.DETECTION.toString()].includes(type);
};
