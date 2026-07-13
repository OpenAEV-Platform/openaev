import { TerminalOutlined } from '@mui/icons-material';

import InjectIcon from '../../common/injects/InjectIcon';

interface ActionTypeIconProps {
  injectorType?: string;
  payloadType?: string;
  isPayload?: boolean;
}

/**
 * Displays the appropriate icon for an action/step:
 * - InjectIcon when a payload or injector type is available
 * - TerminalOutlined as generic command
 */
const ActionTypeIcon = ({ injectorType, payloadType, isPayload }: ActionTypeIconProps) => {
  const type = payloadType ?? injectorType;
  if (type) {
    return <InjectIcon type={type} isPayload={isPayload} size="small" />;
  }
  return (
    <TerminalOutlined sx={{
      fontSize: 16,
      color: 'primary.main',
    }}
    />
  );
};

export default ActionTypeIcon;
