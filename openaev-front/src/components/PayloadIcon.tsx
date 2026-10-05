import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { DnsOutlined, SubscriptionsOutlined } from '@mui/icons-material';
import { ApplicationCogOutline, Console, FileImportOutline, LanConnect } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

interface PayloadIconProps {
  payloadType: string;
  tooltip?: boolean;
}

const renderIcon = (payloadType: string) => {
  switch (payloadType) {
    case 'Command':
      return <Console color="primary" />;
    case 'Executable':
      return <ApplicationCogOutline color="primary" />;
    case 'FileDrop':
      return <FileImportOutline color="primary" />;
    case 'DnsResolution':
      return <DnsOutlined color="primary" />;
    case 'NetworkTraffic':
      return <LanConnect color="primary" />;
    default:
      return <SubscriptionsOutlined color="primary" />;
  }
};
const PayloadIcon: FunctionComponent<PayloadIconProps> = ({ payloadType, tooltip = false }) => {
  if (tooltip) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="inline-flex">{renderIcon(payloadType)}</span>
        </TooltipTrigger>
        <TooltipContent>{payloadType}</TooltipContent>
      </Tooltip>
    );
  }
  return renderIcon(payloadType);
};

export default PayloadIcon;
