import { useEffect, useState } from 'react';

import { type PayloadUsageMoveOutput, type PayloadUsageOutput, type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';
import useEntityToggle from '../../../../../utils/hooks/useEntityToggle';
import { moveTargets, moveUsages } from './usageMove';

interface Options {
  source: PayloadVersionOutput;
  versions: PayloadVersionOutput[];
  usages: PayloadUsagesOutput | null;
  onMoved: () => void;
}

/** Picking usages of a version and moving them to another one: the result says which stayed, and why. */
const useUsageMove = ({ source, versions, usages, onMoved }: Options) => {
  const rows = usages?.payload_usages ?? [];
  const { targets, defaultTarget } = moveTargets(versions, source.payload_id);
  const [target, setTarget] = useState(defaultTarget);
  const [moving, setMoving] = useState(false);
  const [result, setResult] = useState<PayloadUsageMoveOutput | null>(null);
  const selection = useEntityToggle<PayloadUsageOutput>('usage', rows, rows.length);
  useEffect(() => setTarget(defaultTarget), [defaultTarget]);
  useEffect(() => selection.handleClearSelectedElements(), [usages]);

  const chosen = selection.selectAll
    ? rows.filter(usage => !(usage.usage_id in selection.deSelectedElements))
    : Object.values(selection.selectedElements);

  const move = async () => {
    setMoving(true);
    try {
      setResult(await moveUsages(source.payload_action_id, target, chosen));
      onMoved();
    } finally {
      setMoving(false);
    }
  };

  return {
    targets,
    target,
    setTarget,
    selection,
    chosenCount: chosen.length,
    canMove: chosen.length > 0 && target !== '' && !moving,
    moving,
    move,
    result,
    clearResult: () => setResult(null),
    // Refused usages stay on this version, so the list still names them.
    nameOf: (usageId: string) => rows.find(usage => usage.usage_id === usageId)?.usage_name ?? usageId,
  };
};

export type UsageMove = ReturnType<typeof useUsageMove>;

export default useUsageMove;
