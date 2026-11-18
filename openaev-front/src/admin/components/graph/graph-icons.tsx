import {
  BugReportOutlined,
  DnsOutlined,
  DomainOutlined,
  GroupsOutlined,
  PersonOutlined,
  PlayCircleOutlineOutlined,
  RouteOutlined,
} from '@mui/icons-material';
import { SelectGroup, Target } from 'mdi-material-ui';
import { type ReactElement } from 'react';

import { type GraphNodeType } from './graph-model';

/** The icon of each node type, the same as its entry in the left menu. */
const NODE_ICON: Record<GraphNodeType, () => ReactElement> = {
  ASSET: () => <DnsOutlined fontSize="small" />,
  ASSET_GROUP: () => <SelectGroup fontSize="small" />,
  FINDING: () => <BugReportOutlined fontSize="small" />,
  INJECT: () => <Target fontSize="small" />,
  SIMULATION: () => <PlayCircleOutlineOutlined fontSize="small" />,
  SCENARIO: () => <RouteOutlined fontSize="small" />,
  PERSON: () => <PersonOutlined fontSize="small" />,
  ORGANIZATION: () => <DomainOutlined fontSize="small" />,
  TEAM: () => <GroupsOutlined fontSize="small" />,
};

export default NODE_ICON;
