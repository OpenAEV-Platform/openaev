import { type AggregatedFindingOutput, type InjectorSimple } from '../../../utils/api-types';

// Stable finding as returned by /api/stable-findings: one finding = one location (US.0.0).
export type StableFindingRow = AggregatedFindingOutput & {
  finding_legacy_id?: string;
  finding_location?: string;
  finding_location_type?: 'ASSET' | 'USER' | 'TEAM' | 'RESOURCE';
  finding_location_key?: string;
  finding_occurrences?: number;
  finding_sources?: InjectorSimple[];
};

// Asset column (US.1): the asset of the finding. A cloud finding shows its resource, a finding on
// a user (e.g. credentials captured by phishing) shows "-".
export const findingAssetLabel = (finding: StableFindingRow): string => {
  const asset = finding.finding_assets?.[0]?.asset_name;
  if (asset) return asset;
  if (finding.finding_location_type === 'RESOURCE' && finding.finding_location) {
    return finding.finding_location;
  }
  return '-';
};

// Every injector that detected the finding, first detection first.
export const findingSources = (finding: StableFindingRow): InjectorSimple[] => {
  if (finding.finding_sources && finding.finding_sources.length > 0) return finding.finding_sources;
  return finding.finding_source ? [finding.finding_source] : [];
};

// Source column (US.1): the first injector, then "+N"; "-" when no injector detected it.
export const findingSourceLabel = (finding: StableFindingRow): string => {
  const sources = findingSources(finding);
  if (sources.length === 0) return '-';
  const first = sources[0].injector_name ?? '-';
  return sources.length > 1 ? `${first} +${sources.length - 1}` : first;
};

// Location as shown in the finding detail (US.4, decided 2026-11-19): asset, user or resource.
export const findingLocationLabel = (finding: StableFindingRow): string => {
  const kind = finding.finding_location_type;
  const name = finding.finding_assets?.[0]?.asset_name ?? finding.finding_location ?? finding.finding_location_key;
  if (!name) return '-';
  if (kind === 'USER') return `${name} (user)`;
  if (kind === 'RESOURCE') return `${name} (cloud resource)`;
  return name;
};
