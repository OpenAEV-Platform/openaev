import { type KillChainPhase } from '../../utils/api-types';

export interface KillChainPhaseHelper {
  getKillChainPhases: () => KillChainPhase[];
  getKillChainPhasesMap: () => Record<string, KillChainPhase>;
}
