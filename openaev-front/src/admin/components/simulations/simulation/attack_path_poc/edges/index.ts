import { type EdgeTypes } from '@xyflow/react';

import { AP_FLOW_EDGE_TYPE } from '../attack-path-poc-flow-helpers';
import GroupedEdge from './GroupedEdge';

const edgeTypes: EdgeTypes = { [AP_FLOW_EDGE_TYPE]: GroupedEdge };

export default edgeTypes;
