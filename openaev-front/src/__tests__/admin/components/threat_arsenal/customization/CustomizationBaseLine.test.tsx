import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import CustomizationBaseLine from '../../../../../admin/components/threat_arsenal/customization/CustomizationBaseLine';
import { renderInApp } from '../threatArsenalTestUtils';

describe('CustomizationBaseLine', () => {
  afterEach(cleanup);

  it('given a newer Filigran version should offer to review the update', () => {
    // Arrange
    const onReview = vi.fn();
    renderInApp(
      <CustomizationBaseLine
        customization={{
          customization_base_action_id: 'base-action',
          customization_base_version: '1.0.0',
          customization_update: {
            update_payload_id: 'filigran-1.1.0',
            update_version: '1.1.0',
            update_fields: [],
            update_other_changes: [],
          },
        }}
        onReview={onReview}
      />,
    );

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Update' }));

    // Assert
    expect(screen.getByText('Based on v1.0.0 · Filigran v1.1.0 is available')).toBeTruthy();
    expect(onReview).toHaveBeenCalled();
  });

  it('given no newer Filigran version should only tell the base', () => {
    // Act
    renderInApp(
      <CustomizationBaseLine
        customization={{
          customization_base_action_id: 'base-action',
          customization_base_version: '1.0.0',
        }}
        onReview={vi.fn()}
      />,
    );

    // Assert
    expect(screen.getByText('Based on v1.0.0')).toBeTruthy();
    expect(screen.queryByRole('button')).toBeNull();
  });
});
