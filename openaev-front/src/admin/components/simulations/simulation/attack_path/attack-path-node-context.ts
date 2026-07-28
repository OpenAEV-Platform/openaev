import { createContext } from 'react';

// Provides the endpoint-detail action to attack-path node components. ReactFlow renders custom nodes
// without direct props, and a node's MUI Tooltip renders in a portal, so a "Details" button inside it
// cannot bubble to ReactFlow's onNodeClick; AttackPathFlow sets this from its onEndpointClick prop and
// AssetNode consumes it. Undefined outside a provider (the button is then simply inert).
const EndpointActionContext = createContext<
  ((nodeId: string, ref?: string, label?: string) => void) | undefined
>(undefined);

export default EndpointActionContext;
