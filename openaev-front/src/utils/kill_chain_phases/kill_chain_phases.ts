import { type KillChainPhase } from '../api-types';

export const sortKillChainPhase = (k1: Pick<KillChainPhase, 'phase_order'>, k2: Pick<KillChainPhase, 'phase_order'>) => {
  return (k1.phase_order ?? 0) - (k2.phase_order ?? 0);
};
