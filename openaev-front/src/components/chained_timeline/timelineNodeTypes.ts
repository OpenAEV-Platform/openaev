import { type NodeTypes } from '@xyflow/react';

import TimelineInjectNode from './TimelineInjectNode';

// Kept out of the component file so react-refresh keeps working there.
const timelineNodeTypes: NodeTypes = { inject: TimelineInjectNode };

export default timelineNodeTypes;
