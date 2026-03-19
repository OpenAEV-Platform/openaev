import { BugReport, HelpOutlined, Newspaper, Person, RowingOutlined, ShieldOutlined, TrackChanges } from '@mui/icons-material';
import { type ReactElement } from 'react';

export default function expectationIconByType(expectationType: string | undefined): ReactElement {
  switch (expectationType) {
    case 'prevention':
      return <ShieldOutlined fontSize="small" />;
    case 'detection':
      return <TrackChanges fontSize="small" />;
    case 'vulnerability':
      return <BugReport fontSize="small" />;
    case 'manual':
      return <Person fontSize="small" />;
    case 'article':
      return <Newspaper fontSize="small" />;
    case 'challenge':
      return <RowingOutlined fontSize="small" />;
    default:
      return <HelpOutlined fontSize="small" />;
  }
};
