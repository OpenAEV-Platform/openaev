import { ExtensionOutlined, TerminalOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { type AvailableActionOutput, type AvailableIntegrationActionOutput } from '../../../../utils/api-types';
import { byKind } from './availableActions';

export const PayloadActionIcon = () => <TerminalOutlined color="primary" />;

interface IntegrationProps { action: AvailableIntegrationActionOutput }

export const IntegrationActionIcon: FunctionComponent<IntegrationProps> = ({ action }) => (action.action_integration_logo_url
  ? <img src={`/api/images/catalog/connectors/logos/${action.action_integration_logo_url}`} alt={action.action_integration_title} />
  : <ExtensionOutlined color="primary" />);

const iconOf = byKind({
  PAYLOAD: () => <PayloadActionIcon />,
  INTEGRATION: action => <IntegrationActionIcon action={action} />,
});

interface Props { action: AvailableActionOutput }

// A payload shows a terminal, an integration action the logo of its integration.
const AvailableActionIcon: FunctionComponent<Props> = ({ action }) => iconOf(action);

export default AvailableActionIcon;
