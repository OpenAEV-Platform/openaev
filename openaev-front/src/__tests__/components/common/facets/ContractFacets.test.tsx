import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import ContractOutputElementType, { CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS } from '../../../../admin/components/findings/ContractOutputElementType';
import { buildAssetRows, buildFindingTypeRows } from '../../../../components/common/facets/ContractFacets';
import { type Option } from '../../../../utils/api-types';

// -- HELPERS --

const t = (key: string) => key;

const typeRows = (overrides: Partial<Parameters<typeof buildFindingTypeRows>[0]> = {}) => buildFindingTypeRows({
  t,
  statusValues: [],
  statusCounts: undefined,
  toggle: vi.fn(),
  ...overrides,
});

// -- TESTS --

describe('buildFindingTypeRows', () => {
  it('should list every finding type, sorted by label', () => {
    // Act
    const rows = typeRows();

    // Assert
    expect(rows).toHaveLength(CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS.length);
    const labels = rows.map(row => row.label);
    expect(labels).toEqual([...labels].sort((a, b) => a.localeCompare(b)));
    expect(rows.find(row => row.value === 'ipv4')?.label).toBe(ContractOutputElementType.ipv4);
  });

  it('should check the selected types and toggle the value of the clicked row', () => {
    // Arrange
    const toggle = vi.fn();
    const rows = typeRows({
      statusValues: ['ipv4'],
      toggle,
    });

    // Act
    rows.find(row => row.value === 'cve')?.onToggle();

    // Assert
    expect(rows.filter(row => row.checked).map(row => row.value)).toEqual(['ipv4']);
    expect(toggle).toHaveBeenCalledWith('cve');
  });

  it('should only carry a count when counts are given, defaulting to 0', () => {
    // Act
    const withoutCounts = typeRows();
    const withCounts = typeRows({ statusCounts: { ipv4: 3 } });

    // Assert
    expect(withoutCounts.every(row => row.count === undefined)).toBe(true);
    expect(withCounts.find(row => row.value === 'ipv4')?.count).toBe(3);
    expect(withCounts.find(row => row.value === 'cve')?.count).toBe(0);
  });

  it('should give each row the icon of its finding type', () => {
    // Arrange
    const row = typeRows().find(r => r.value === 'ipv4');

    // Act
    const { container } = render(<>{row?.icon?.()}</>);

    // Assert
    expect(container.querySelector('svg')).not.toBeNull();
  });
});

describe('buildAssetRows', () => {
  const toggle = vi.fn();
  const options: Option[] = [
    {
      id: 'asset-b',
      label: 'Beta',
    },
    {
      id: 'asset-a',
      label: 'Alpha',
    },
    { id: 'asset-no-label' },
    { label: 'No id' },
  ];

  it('should keep the options with an id and a label, sorted by label', () => {
    // Act
    const rows = buildAssetRows({
      assetOptions: options,
      selectedIds: [],
      toggle,
    });

    // Assert
    expect(rows.map(row => [row.value, row.label])).toEqual([
      ['asset-a', 'Alpha'],
      ['asset-b', 'Beta'],
    ]);
  });

  it('should check the rows whose id is selected and toggle the clicked id', () => {
    // Arrange
    const rows = buildAssetRows({
      assetOptions: options,
      selectedIds: ['asset-b'],
      toggle,
    });

    // Act
    rows.find(row => row.label === 'Alpha')?.onToggle();

    // Assert
    expect(rows.filter(row => row.checked).map(row => row.value)).toEqual(['asset-b']);
    expect(toggle).toHaveBeenCalledWith('asset-a');
  });

  it('should tell apart two assets sharing the same label', () => {
    // Arrange
    const homonyms: Option[] = [
      {
        id: 'asset-1',
        label: 'localhost',
      },
      {
        id: 'asset-2',
        label: 'localhost',
      },
    ];

    // Act
    const rows = buildAssetRows({
      assetOptions: homonyms,
      selectedIds: ['asset-2'],
      toggle,
    });

    // Assert
    expect(rows.filter(row => row.checked).map(row => row.value)).toEqual(['asset-2']);
  });

  it('should return no row when there is no option', () => {
    // Act
    const rows = buildAssetRows({
      assetOptions: [],
      selectedIds: [],
      toggle,
    });

    // Assert
    expect(rows).toEqual([]);
  });
});
