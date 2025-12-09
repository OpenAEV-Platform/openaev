import ConnectorLayout from '../common/ConnectorLayout';
import ConnectorProvider from '../common/ConnectorProvider';

const InjectorsLayout = () => (
  <ConnectorProvider type="INJECTOR">
    <ConnectorLayout />
  </ConnectorProvider>
);

export default InjectorsLayout;
