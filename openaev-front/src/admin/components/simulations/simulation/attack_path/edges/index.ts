import { type EdgeTypes } from '@xyflow/react';

import { AP_FLOW_CAUSAL_EDGE_TYPE, AP_FLOW_EDGE_TYPE } from '../attack-path-flow-helpers';
import CausalEdge from './CausalEdge';
import GroupedEdge from './GroupedEdge';

const edgeTypes: EdgeTypes = {
  [AP_FLOW_EDGE_TYPE]: GroupedEdge,
  [AP_FLOW_CAUSAL_EDGE_TYPE]: CausalEdge,
};

export default edgeTypes;
