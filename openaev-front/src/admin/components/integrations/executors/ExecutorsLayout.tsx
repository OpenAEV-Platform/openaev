import ConnectorLayout from '../common/ConnectorLayout';
import ConnectorProvider from '../common/ConnectorProvider';

const ExecutorsLayout = () => (
  <ConnectorProvider type="EXECUTOR">
    <ConnectorLayout />
  </ConnectorProvider>
);

export default ExecutorsLayout;
