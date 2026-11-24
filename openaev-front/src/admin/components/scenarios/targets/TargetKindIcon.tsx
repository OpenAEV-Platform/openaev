import { AccountTreeOutlined, DnsOutlined, GroupsOutlined, type SvgIconComponent } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { type TargetKind } from './scenarioTargets';

const ICONS: Record<TargetKind, SvgIconComponent> = {
  ASSETS: DnsOutlined,
  ASSET_GROUPS: DnsOutlined,
  HOSTS: AccountTreeOutlined,
  TEAMS: GroupsOutlined,
};

interface Props { kind: TargetKind }

const TargetKindIcon: FunctionComponent<Props> = ({ kind }) => {
  const Icon = ICONS[kind];
  return <Icon fontSize="small" />;
};

export default TargetKindIcon;
