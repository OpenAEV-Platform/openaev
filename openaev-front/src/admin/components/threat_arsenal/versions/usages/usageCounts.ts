import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

/** Every usage of a version, the ones the user cannot see included. */
export const usageCount = (usages: PayloadUsagesOutput) => usages.payload_usages.length + (usages.payload_usages_hidden_count ?? 0);

/** The scenarios the usages the user can see belong to. */
export const scenarioCount = (usages: PayloadUsagesOutput) => new Set(usages.payload_usages.map(usage => usage.usage_scenario_id).filter(Boolean)).size;

/** Every usage of the versions other than the latest. */
export const olderUsageCount = (versions: PayloadVersionOutput[], latest: PayloadVersionOutput | undefined, usages: Record<string, PayloadUsagesOutput>) => versions
  .filter(version => version.payload_id !== latest?.payload_id && usages[version.payload_id])
  .reduce((sum, version) => sum + usageCount(usages[version.payload_id]), 0);
