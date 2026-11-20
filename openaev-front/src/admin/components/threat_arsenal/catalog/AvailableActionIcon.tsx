import { ExtensionOutlined, TerminalOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { type AvailableActionOutput } from '../../../../utils/api-types';
import { byKind } from './availableActions';

const iconOf = byKind({
  PAYLOAD: () => <TerminalOutlined color="primary" />,
  INTEGRATION: action => (action.action_integration_logo_url
    ? <img src={`/api/images/catalog/connectors/logos/${action.action_integration_logo_url}`} alt={action.action_integration_title} />
    : <ExtensionOutlined color="primary" />),
});

interface Props { action: AvailableActionOutput }

// A payload shows a terminal, an integration action the logo of its integration.
const AvailableActionIcon: FunctionComponent<Props> = ({ action }) => iconOf(action);

export default AvailableActionIcon;
