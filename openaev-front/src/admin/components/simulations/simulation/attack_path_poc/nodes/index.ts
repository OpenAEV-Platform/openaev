import { type NodeTypes } from '@xyflow/react';

import { AP_FLOW_NODE_TYPE } from '../attack-path-poc-flow-helpers';
import AssetNode from './AssetNode';
import FindingNode from './FindingNode';
import FindingTypeNode from './FindingTypeNode';
import InjectorNode from './InjectorNode';

const nodeTypes: NodeTypes = {
  [AP_FLOW_NODE_TYPE.injector]: InjectorNode,
  [AP_FLOW_NODE_TYPE.asset]: AssetNode,
  [AP_FLOW_NODE_TYPE.findingType]: FindingTypeNode,
  [AP_FLOW_NODE_TYPE.finding]: FindingNode,
};

export default nodeTypes;
