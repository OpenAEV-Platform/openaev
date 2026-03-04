import { type NodeTypes } from '@xyflow/react';

import NodeAction from './NodeAction';
import NodeEvent from './NodeEvent';

const logicNodeTypes: NodeTypes = {
  action: NodeAction,
  event: NodeEvent,
};

export default logicNodeTypes;
