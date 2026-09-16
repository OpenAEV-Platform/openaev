export interface OCSFResourceWithCloudPartition { cloud_partition?: string | null }

export const OCSF_PROVIDER_CONFLICT = 'unknown';

// A finding can reference multiple resources. Blank partitions do not override useful data;
// conflicting non-blank partitions must remain visible as unknown rather than being attributed
// to whichever resource happened to appear first.
export const resolveOCSFCloudPartition = (
  resources: readonly OCSFResourceWithCloudPartition[],
): string | undefined => {
  const partitions = resources
    .map(resource => resource.cloud_partition?.trim().toLowerCase())
    .filter((partition): partition is string => !!partition);
  if (partitions.length === 0) {
    return undefined;
  }
  return partitions.every(partition => partition === partitions[0])
    ? partitions[0]
    : OCSF_PROVIDER_CONFLICT;
};
