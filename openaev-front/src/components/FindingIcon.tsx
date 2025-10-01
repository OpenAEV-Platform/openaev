import { ReportProblemOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { CubeScan, FormatText, IpOutline, KeyOutline, MidiPort, Numeric, TagSearchOutline } from 'mdi-material-ui';
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
    case 'cve':
      return <ReportProblemOutlined color="primary" />;
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
