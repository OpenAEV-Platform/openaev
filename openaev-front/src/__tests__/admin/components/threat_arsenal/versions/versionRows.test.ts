import { describe, expect, it } from 'vitest';

import { notInstalledVersions, rowValue, versionRows } from '../../../../../admin/components/threat_arsenal/versions/versionRows';
import { type CatalogPayloadOutput } from '../../../../../utils/api-types';
import { aVersion } from '../threatArsenalTestUtils';

const catalogueVersion = (version: string): CatalogPayloadOutput => ({
  catalog_payload_external_id: 'mimikatz',
  catalog_payload_version: version,
});

describe('versionRows', () => {
  it('given installed and not installed versions should sort them newest first, dotted numbers as numbers', () => {
    // Arrange
    const installed = [aVersion({
      payload_id: 'payload-v1.9',
      payload_version: '1.9.0',
    }), aVersion({ payload_version: '1.0.0' })];

    // Act
    const rows = versionRows(installed, ['1.10.0', '1.2.0']);

    // Assert
    expect(rows.map(row => row.version)).toEqual(['1.10.0', '1.9.0', '1.2.0', '1.0.0']);
    expect(rows.map(rowValue)).toEqual(['1.10.0', 'payload-v1.9', '1.2.0', 'payload-v1']);
  });

  it('given catalogue versions should keep the ones not installed', () => {
    // Act
    const versions = notInstalledVersions(
      [catalogueVersion('3'), catalogueVersion('2'), catalogueVersion('1')],
      [aVersion({ payload_version: '2' })],
    );

    // Assert
    expect(versions).toEqual(['3', '1']);
  });
});
