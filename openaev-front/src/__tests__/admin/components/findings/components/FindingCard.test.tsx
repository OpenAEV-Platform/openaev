import '@testing-library/jest-dom/vitest';

import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import FindingCard from '../../../../../admin/components/findings/components/FindingCard';
import { aFinding, renderInApp } from '../findingTestUtils';

const anAsset = (id: string) => ({
  asset_id: id,
  asset_name: `Host ${id}`,
});

describe('FindingCard', () => {
  afterEach(cleanup);

  it('should link to the finding and show its type, value and last seen date', () => {
    // Arrange + Act
    const { container } = renderInApp(<FindingCard finding={aFinding()} />);

    // Assert
    expect(container.querySelector('a[href="/admin/findings/finding-1"]')).not.toBeNull();
    expect(screen.getByText('IPv4')).toBeInTheDocument();
    expect(screen.getByText('10.0.0.1')).toBeInTheDocument();
    expect(screen.getByText('Last seen:')).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it('should count the impacted assets', () => {
    // Arrange + Act
    renderInApp(<FindingCard finding={aFinding({ finding_assets: [anAsset('1'), anAsset('2'), anAsset('3')] })} />);

    // Assert
    expect(screen.getByText('Impacted assets')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('should count 0 impacted assets when the finding has none', () => {
    // Arrange + Act
    renderInApp(<FindingCard finding={aFinding({ finding_assets: undefined as never })} />);

    // Assert
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('should fall back to the raw type of a finding whose type has no label', () => {
    // Arrange + Act
    renderInApp(<FindingCard finding={aFinding({ finding_type: 'brand_new' as never })} />);

    // Assert
    expect(screen.getByText('brand_new')).toBeInTheDocument();
  });
});
