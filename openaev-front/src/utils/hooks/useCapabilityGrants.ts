import { useCallback, useMemo } from 'react';

import { type CapabilityOutput } from '../api-types';
import useAuth from './useAuth';

const BYPASS = 'BYPASS';
const NO_CAPABILITIES: CapabilityOutput[] = [];

const collectCheckableValues = (tree: CapabilityOutput[]): string[] => {
  const values: string[] = [];
  const walk = (node: CapabilityOutput) => {
    if (node.capability_checkable && node.capability_value) {
      values.push(node.capability_value);
    }
    node.capability_children?.forEach(walk);
  };
  tree.forEach(walk);
  return values;
};

// Front mirror of PrivilegeEscalationValidator. `user_capabilities` expands BYPASS into the whole
// scope without ever reporting BYPASS itself, so holding the whole tree is what identifies a holder.
const useCapabilityGrants = (capabilityTree: CapabilityOutput[] = NO_CAPABILITIES) => {
  const { me } = useAuth();

  const heldCapabilities = useMemo(
    () => new Set<string>((me.user_capabilities ?? []) as string[]),
    [me.user_capabilities],
  );

  const isUnrestricted = useMemo(() => {
    if (me.user_admin) {
      return true;
    }
    const grantable = collectCheckableValues(capabilityTree).filter(value => value !== BYPASS);
    return grantable.length > 0 && grantable.every(value => heldCapabilities.has(value));
  }, [me.user_admin, capabilityTree, heldCapabilities]);

  const missingCapabilities = useCallback(
    (capabilities: string[]) => (isUnrestricted
      ? []
      : capabilities.filter(capability => !heldCapabilities.has(capability))),
    [isUnrestricted, heldCapabilities],
  );

  const holdsCapability = useCallback(
    (capability?: string) => !capability || missingCapabilities([capability]).length === 0,
    [missingCapabilities],
  );

  return {
    holdsCapability,
    missingCapabilities,
    isUnrestricted,
  };
};

export default useCapabilityGrants;
