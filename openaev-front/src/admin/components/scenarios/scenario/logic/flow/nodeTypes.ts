import { type NodeTypes } from '@xyflow/react';

import NodeAction from './NodeAction';
import NodeColumnBg from './NodeColumnBg';
import NodeEvent from './NodeEvent';

const logicNodeTypes: NodeTypes = {
  action: NodeAction,
  event: NodeEvent,
  'column-bg': NodeColumnBg,
};

export default logicNodeTypes;
