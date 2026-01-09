import { ReactElement } from 'react';
import { ShieldOutlined, TrackChanges, BugReport, Person, Newspaper, RowingOutlined,HelpOutlined } from '@mui/icons-material';

export const expectationIconByType= (expectationType: string | undefined): ReactElement => {
  switch (expectationType){
    case 'prevention':
      return <ShieldOutlined fontSize="small"/>;
    case 'detection':
      return <TrackChanges fontSize="small"/>;
    case 'vulnerability':
      return <BugReport fontSize="small"/>;
    case 'manual':
      return <Person fontSize="small"/>;
    case 'article':
      return <Newspaper fontSize="small"/>;
    case 'challenge':
      return <RowingOutlined fontSize="small"/>;
    default:
      return <HelpOutlined fontSize="small"/>;
  }
};