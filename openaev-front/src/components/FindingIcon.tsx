import { ReportProblemOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import {
  AccountAlertOutline,
  AccountArrowRight,
  AccountGroupOutline,
  AccountOutline,
  CubeScan,
  DesktopClassic,
  FolderNetworkOutline,
  FormatText,
  Identifier,
  IpOutline,
  KeyOutline,
  MidiPort,
  Numeric,
  ShieldLockOutline,
  TagSearchOutline,
} from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

interface FindingIconProps {
  findingType: string;
  tooltip?: boolean;
}

const renderIcon = (findingType: string) => {
  switch (findingType) {
    case 'text':
      return <FormatText color="primary" />;
    case 'number':
      return <Numeric color="primary" />;
    case 'port':
      return <MidiPort color="primary" />;
    case 'portscan':
      return <CubeScan color="primary" />;
    case 'ipv4':
      return <IpOutline color="primary" />;
    case 'ipv6':
      return <IpOutline color="primary" />;
    case 'credentials':
      return <KeyOutline color="primary" />;
    case 'vulnerability':
      return <ReportProblemOutlined color="primary" />;
    case 'username':
    case 'admin_username':
      return <AccountOutline color="primary" />;
    case 'share':
      return <FolderNetworkOutline color="primary" />;
    case 'group':
      return <AccountGroupOutline color="primary" />;
    case 'computer':
      return <DesktopClassic color="primary" />;
    case 'password_policy':
      return <ShieldLockOutline color="primary" />;
    case 'delegation':
      return <AccountArrowRight color="primary" />;
    case 'sid':
      return <Identifier color="primary" />;
    case 'account_with_password_not_required':
    case 'asreproastable_account':
    case 'kerberoastable_account':
      return <AccountAlertOutline color="primary" />;
    default:
      return <TagSearchOutline color="primary" />;
  }
};
const FindingIcon: FunctionComponent<FindingIconProps> = ({ findingType, tooltip = false }) => {
  if (tooltip) {
    return (
      <Tooltip title={findingType}>
        {renderIcon(findingType)}
      </Tooltip>
    );
  }
  return renderIcon(findingType);
};

export default FindingIcon;
