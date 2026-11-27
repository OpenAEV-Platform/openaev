import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

/** How many usages each version has, by payload id; a version nothing runs is missing. */
export type UsageCounts = Record<string, number>;

export interface OlderVersionInUse {
  version: PayloadVersionOutput;
  usages: PayloadUsagesOutput;
}

/** Every usage of a version, the ones the user cannot see included. */
export const usageCount = (usages: PayloadUsagesOutput) => usages.payload_usages.length + (usages.payload_usages_hidden_count ?? 0);

/** The scenarios the usages the user can see belong to. */
export const scenarioCount = (usages: PayloadUsagesOutput) => new Set(usages.payload_usages.map(usage => usage.usage_scenario_id).filter(Boolean)).size;

export const totalUsageCount = (olderVersions: OlderVersionInUse[]) => olderVersions.reduce((sum, { usages }) => sum + usageCount(usages), 0);

/** The versions other than the one of `actionId` that still have usages. */
export const olderVersionsInUse = (versions: PayloadVersionOutput[], counts: UsageCounts, actionId: string) => versions
  .filter(version => version.payload_action_id !== actionId && (counts[version.payload_id] ?? 0) > 0);

/** How many usages are left on the versions other than the one of `actionId`. */
export const olderUsageCount = (versions: PayloadVersionOutput[], counts: UsageCounts, actionId: string) => olderVersionsInUse(versions, counts, actionId)
  .reduce((sum, version) => sum + counts[version.payload_id], 0);
