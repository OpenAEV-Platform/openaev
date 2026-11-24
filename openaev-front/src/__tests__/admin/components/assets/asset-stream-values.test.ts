import { describe, expect, it } from 'vitest';

import { withoutNullValues } from '../../../../admin/components/assets/asset-stream-values';

describe('withoutNullValues', () => {
  it('given_nullFields_should_dropThemSoTheFormDefaultsApply', () => {
    // Arrange
    const streamed = {
      asset_name: 'host',
      asset_url: null,
      asset_cloud_region: null,
      asset_tags: [],
      endpoint_is_eol: false,
    };

    // Act
    const values = withoutNullValues(streamed);

    // Assert
    expect(values).toEqual({
      asset_name: 'host',
      asset_tags: [],
      endpoint_is_eol: false,
    });
  });
});
