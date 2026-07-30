import { DnsOutlined, DomainOutlined, GroupsOutlined, PersonOutlined, PlayCircleOutlineOutlined, RouteOutlined } from '@mui/icons-material';
import { SecurityNetwork, SelectGroup } from 'mdi-material-ui';

// Keep in sync with the left menu (LeftBar.tsx): search results must display
// the same icon as the section the entity lives in.
const useEntityIcon = (entity: string) => {
  switch (entity) {
    case 'Asset':
      return (<DnsOutlined color="primary" />);
    case 'AssetGroup':
      return (<SelectGroup color="primary" />);
    case 'SecurityPlatform':
      return (<SecurityNetwork color="primary" />);
    case 'User':
      return (<PersonOutlined color="primary" />);
    case 'Team':
      return (<GroupsOutlined color="primary" />);
    case 'Organization':
      return (<DomainOutlined color="primary" />);
    case 'Scenario':
      return (<RouteOutlined color="primary" />);
    case 'Exercise':
      return (<PlayCircleOutlineOutlined color="primary" />);
    default:
      return null;
  }
};

export default useEntityIcon;
