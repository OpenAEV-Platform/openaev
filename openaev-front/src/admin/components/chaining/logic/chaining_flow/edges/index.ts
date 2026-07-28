import { type EdgeTypes } from '@xyflow/react';

import DeletableEdge from './DeletableEdge';
import InformationalEdge from './InformationalEdge';

const edgeTypes: EdgeTypes = {
  deletable: DeletableEdge,
  informational: InformationalEdge,
};

export default edgeTypes;
