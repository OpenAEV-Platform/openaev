import { useMemo, useState } from 'react';

import { type KillChainPhaseHelper } from '../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../store';
import { type KillChainPhase } from '../../../../utils/api-types';

/**
 * Distinct kill chains (MITRE ATT&CK, MITRE ATLAS, ...) with the active selection. The matrix
 * shows ONE at a time; ATT&CK first (the most common), then the other kill chains alphabetically
 * (same order as the contract picker sidebar and the security coverage widget). Lifted into a
 * hook so the selector can live in the drawer header while the matrix fills the body.
 */
const useKillChains = () => {
  const { killChainPhases } = useHelper((helper: KillChainPhaseHelper) => ({ killChainPhases: helper.getKillChainPhases() }));
  const killChains = useMemo<string[]>(
    () => [...new Set(killChainPhases.map((p: KillChainPhase) => p.phase_kill_chain_name))]
      .filter((name): name is string => !!name)
      .sort((a, b) => {
        const aAttack = a.toLowerCase().includes('attack');
        const bAttack = b.toLowerCase().includes('attack');
        if (aAttack !== bAttack) return aAttack ? -1 : 1;
        return a.localeCompare(b);
      }),
    [killChainPhases],
  );
  const defaultKillChain = useMemo(
    () => killChains.find(chain => chain.toLowerCase().includes('attack')) ?? killChains[0],
    [killChains],
  );
  const [selectedKillChain, setSelectedKillChain] = useState<string | null>(null);
  const activeKillChain = selectedKillChain != null && killChains.includes(selectedKillChain)
    ? selectedKillChain
    : defaultKillChain;
  return {
    killChains,
    activeKillChain,
    selectKillChain: setSelectedKillChain,
  };
};

export default useKillChains;
