import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';

import { type EsAttackPath } from '../../../../../../../utils/api-types';
import AttackPatternBox from '../AttackPatternBox';

export type NodeAttackPath = Node<{
  attackPath: EsAttackPath;
  onHover: (nodeId: string | null) => void;
  onLeave: () => void;
}>;

const AttackPatternNode = (node: NodeProps<NodeAttackPath>) => {
  const attackPath: EsAttackPath = node.data.attackPath;

  return (
    <div
      onMouseEnter={() => node.data.onHover(node.id)}
      onMouseLeave={() => node.data.onLeave()}
    >
      <AttackPatternBox
        attackPatternName={attackPath.attackPatternName}
        attackPatternExternalId={attackPath.attackPatternExternalId ?? ''}
        successRate={attackPath.value !== null && attackPath.value !== undefined ? attackPath.value / 100 : null}
        style={{ width: '150px' }}
      />
      <Handle type="target" position={Position.Left} isConnectable />
      <Handle type="source" position={Position.Right} isConnectable />
    </div>
  );
};

export default AttackPatternNode;
