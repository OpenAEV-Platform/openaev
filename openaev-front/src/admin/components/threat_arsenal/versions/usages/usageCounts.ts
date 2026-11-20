import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

export interface OlderVersionInUse {
  version: PayloadVersionOutput;
  usages: PayloadUsagesOutput;
}

/** Every usage of a version, the ones the user cannot see included. */
export const usageCount = (usages: PayloadUsagesOutput) => usages.payload_usages.length + (usages.payload_usages_hidden_count ?? 0);

/** The scenarios the usages the user can see belong to. */
export const scenarioCount = (usages: PayloadUsagesOutput) => new Set(usages.payload_usages.map(usage => usage.usage_scenario_id).filter(Boolean)).size;

export const totalUsageCount = (olderVersions: OlderVersionInUse[]) => olderVersions.reduce((sum, { usages }) => sum + usageCount(usages), 0);
