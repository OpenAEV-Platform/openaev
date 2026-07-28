import ConnectorLayout from '../common/ConnectorLayout';
import ConnectorProvider from '../common/ConnectorProvider';

const SecretsProvidersLayout = () => (
  <ConnectorProvider type="SECRETS_PROVIDER">
    <ConnectorLayout />
  </ConnectorProvider>
);

export default SecretsProvidersLayout;
