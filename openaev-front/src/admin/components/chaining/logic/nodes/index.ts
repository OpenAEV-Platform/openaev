import { type NodeTypes } from '@xyflow/react';

import ActionNode from './ActionNode';
import EventNode from './EventNode';

const nodeTypes: NodeTypes = {
  action: ActionNode,
  event: EventNode,
};

export default nodeTypes;
