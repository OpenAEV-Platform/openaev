import { type AssetCriticality } from '../assets/asset-categories';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - SAMPLE DATA ONLY. Criticality of a remediation or a mitigation.
//
// The scale is the one of the product: the asset criticality (Very high / High / Medium / Low / Unknown), shown
// with the same chip as the assets and the attack path. The weights are the ones of the attack path chokepoints
// (Very high 4, High 3, Medium 2, Low 1).
//
// How it is computed: XTM One rates the weakness (or the behavior) the action exposes (the base). The base is then
// combined with the most critical asset the guidance applies to: the average of the two weights, rounded up. A
// Medium weakness on a Very high asset is High. A Very high weakness on a Low asset is High. An asset whose
// criticality is not set says nothing, so it is ignored (when no asset is known the base is the criticality).
// In the product the asset criticality comes from the asset (`asset_criticality`); here it is sample data.
// ---------------------------------------------------------------------------------------------

export const CRITICALITY_WEIGHT: Record<AssetCriticality, number> = {
  VERY_HIGH: 4,
  HIGH: 3,
  MEDIUM: 2,
  LOW: 1,
  UNKNOWN: 1,
};

const LEVEL_BY_WEIGHT: Record<number, AssetCriticality> = {
  1: 'LOW',
  2: 'MEDIUM',
  3: 'HIGH',
  4: 'VERY_HIGH',
};

// What XTM One rates for the weakness each sample payload exposes.
const BASE_CRITICALITY: Record<string, AssetCriticality> = {
  'kerberoasting': 'HIGH',
  'asrep-roasting': 'HIGH',
  'anonymous-smb-share': 'HIGH',
  'delegation-abuse': 'VERY_HIGH',
  'lsass-dump': 'VERY_HIGH',
  'dcsync': 'VERY_HIGH',
  'nopac': 'VERY_HIGH',
  'llmnr-poisoning': 'MEDIUM',
  'openssh-regresshion': 'VERY_HIGH',
  'ssh-bruteforce': 'MEDIUM',
  'linux-reverse-shell': 'HIGH',
  'linux-credential-files': 'MEDIUM',
  'macos-gatekeeper-bypass': 'MEDIUM',
  'macos-remote-login': 'LOW',
  'log4shell-nuclei-scan': 'VERY_HIGH',
  'zerologon-check': 'VERY_HIGH',
  'service-enumeration': 'LOW',
};

export const baseCriticalityOf = (payloadId: string): AssetCriticality => BASE_CRITICALITY[payloadId] ?? 'MEDIUM';

// Sample criticality of the hosts the prototype talks about (domain controller > finance > web > HR).
const HOST_CRITICALITY: Record<string, AssetCriticality> = {
  'win-dc01': 'VERY_HIGH',
  'win-fin-07': 'HIGH',
  'ubuntu-web-03': 'MEDIUM',
  'win-hr-12': 'LOW',
  'winterfell': 'VERY_HIGH',
  'castelback': 'LOW',
};

// "winterfell (10.0.0.7)" -> "winterfell".
export const hostCriticality = (host: string): AssetCriticality => HOST_CRITICALITY[host.split(' ')[0].toLowerCase()] ?? 'UNKNOWN';

export interface CombinedCriticality {
  level: AssetCriticality;
  // XTM One's rating of the weakness alone.
  base: AssetCriticality;
  // The most critical asset the guidance applies to. Undefined when no asset has a criticality.
  topAsset?: {
    host: string;
    criticality: AssetCriticality;
  };
}

export const combineCriticality = (base: AssetCriticality, hosts: string[]): CombinedCriticality => {
  const known = hosts
    .map(host => ({
      host,
      criticality: hostCriticality(host),
    }))
    .filter(asset => asset.criticality !== 'UNKNOWN');
  if (known.length === 0) {
    return {
      level: base,
      base,
    };
  }
  const topAsset = known.reduce((top, asset) => (CRITICALITY_WEIGHT[asset.criticality] > CRITICALITY_WEIGHT[top.criticality] ? asset : top));
  const weight = Math.ceil((CRITICALITY_WEIGHT[base] + CRITICALITY_WEIGHT[topAsset.criticality]) / 2);
  return {
    level: LEVEL_BY_WEIGHT[weight],
    base,
    topAsset,
  };
};

// Order of a list of guidance: the most critical first.
export const criticalityRank = (level: AssetCriticality) => -CRITICALITY_WEIGHT[level];
