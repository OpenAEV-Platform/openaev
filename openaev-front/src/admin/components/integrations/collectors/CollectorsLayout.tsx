import ConnectorLayout from '../common/ConnectorLayout';
import ConnectorProvider from '../common/ConnectorProvider';

const CollectorsLayout = () => (
  <ConnectorProvider type="COLLECTOR">
    <ConnectorLayout />
  </ConnectorProvider>
);
export default CollectorsLayout;
