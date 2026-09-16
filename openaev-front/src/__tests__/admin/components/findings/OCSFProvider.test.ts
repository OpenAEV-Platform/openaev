import { describe, expect, it } from 'vitest';

import {
  OCSF_PROVIDER_CONFLICT,
  resolveOCSFCloudPartition,
} from '../../../../admin/components/findings/OCSFProvider';

describe('resolveOCSFCloudPartition', () => {
  it('given matching resources should return the normalized first non-blank partition', () => {
    // Arrange
    const resources = [
      { cloud_partition: ' ' },
      { cloud_partition: 'AWS' },
      { cloud_partition: 'aws' },
    ];

    // Act
    const provider = resolveOCSFCloudPartition(resources);

    // Assert
    expect(provider).toBe('aws');
  });

  it('given conflicting resources should return the neutral conflict value', () => {
    // Arrange
    const resources = [
      { cloud_partition: 'aws' },
      { cloud_partition: 'aws-cn' },
    ];

    // Act
    const provider = resolveOCSFCloudPartition(resources);

    // Assert
    expect(provider).toBe(OCSF_PROVIDER_CONFLICT);
  });

  it('given no valid resource partition should return undefined', () => {
    // Arrange
    const resources = [
      {},
      { cloud_partition: null },
      { cloud_partition: ' ' },
    ];

    // Act
    const provider = resolveOCSFCloudPartition(resources);

    // Assert
    expect(provider).toBeUndefined();
  });
});
