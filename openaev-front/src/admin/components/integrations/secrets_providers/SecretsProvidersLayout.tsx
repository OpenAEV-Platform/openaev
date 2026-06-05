import ConnectorLayout from '../common/ConnectorLayout';
import ConnectorProvider from '../common/ConnectorProvider';

const ExecutorsLayout = () => (
  <ConnectorProvider type="SECRETS_PROVIDER">
    <ConnectorLayout />
  </ConnectorProvider>
);

export default ExecutorsLayout;
