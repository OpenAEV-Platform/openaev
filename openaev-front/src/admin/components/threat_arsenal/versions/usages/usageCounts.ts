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

/** The versions other than the one of `actionId` that still have usages. */
export const olderVersionsInUse = (versions: PayloadVersionOutput[], usages: Record<string, PayloadUsagesOutput>, actionId: string): OlderVersionInUse[] => versions
  .filter(version => version.payload_action_id !== actionId)
  .map(version => ({
    version,
    usages: usages[version.payload_id],
  }))
  .filter(older => older.usages && usageCount(older.usages) > 0);
/** Every usage of the versions other than the latest. */
export const olderUsageCount = (versions: PayloadVersionOutput[], latest: PayloadVersionOutput | undefined, usages: Record<string, PayloadUsagesOutput>) => versions
  .filter(version => version.payload_id !== latest?.payload_id && usages[version.payload_id])
  .reduce((sum, version) => sum + usageCount(usages[version.payload_id]), 0);
