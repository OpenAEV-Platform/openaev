import { type NodeTypes } from '@xyflow/react';

import ActionNode from './ActionNode';
import EventNode from './EventNode';
import TacticGroupNode from './TacticGroupNode';

const nodeTypes: NodeTypes = {
  action: ActionNode,
  event: EventNode,
  tacticGroup: TacticGroupNode,
};

export default nodeTypes;
