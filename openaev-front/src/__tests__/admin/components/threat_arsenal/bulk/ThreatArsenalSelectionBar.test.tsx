import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalSelectionBar from '../../../../../admin/components/threat_arsenal/bulk/ThreatArsenalSelectionBar';
import { renderInApp } from '../threatArsenalTestUtils';

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));

describe('ThreatArsenalSelectionBar', () => {
  afterEach(cleanup);

  it.each([
    [1, '1 action selected'],
    [3, '3 actions selected'],
  ])('given %i selected actions should read "%s"', (count, label) => {
    // Act
    renderInApp(<ThreatArsenalSelectionBar count={count} totalElements={10} onClear={vi.fn()} onRunTest={vi.fn()} />);

    // Assert
    expect(screen.getByText(label)).toBeTruthy();
  });
});
