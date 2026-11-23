import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import PayloadUsagesTable from '../../../../../../admin/components/threat_arsenal/versions/usages/PayloadUsagesTable';
import { aUsage, renderInApp, someUsages } from '../../threatArsenalTestUtils';

const manyUsages = (count: number) => Array.from({ length: count }, (_, index) => aUsage({
  usage_id: `usage-${index}`,
  usage_name: `Inject ${index}`,
}));

describe('PayloadUsagesTable', () => {
  afterEach(cleanup);

  it('given no usage should say no inject uses the version', () => {
    // Act
    renderInApp(<PayloadUsagesTable usages={someUsages([])} />);

    // Assert
    expect(screen.getByText('No inject uses this version')).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('given usages of each type should list them with their scenario and type', () => {
    // Act
    renderInApp(
      <PayloadUsagesTable usages={someUsages([
        aUsage(),
        aUsage({
          usage_id: 'usage-2',
          usage_name: undefined,
          usage_scenario_name: undefined,
          usage_type: 'atomic_testing',
        }),
        aUsage({
          usage_id: 'usage-3',
          usage_name: 'Step 1',
          usage_type: 'step',
        }),
      ])}
      />,
    );

    // Assert
    expect(screen.getByRole('table', { name: 'Injects on this version' })).toBeTruthy();
    expect(screen.getByText('Dump credentials')).toBeTruthy();
    expect(screen.getAllByText('Ransomware drill')).toHaveLength(2);
    expect(screen.getAllByText('Inject')).toHaveLength(2);
    expect(screen.getByText('usage-2')).toBeTruthy();
    expect(screen.getByText('-')).toBeTruthy();
    expect(screen.getByText('Atomic testing')).toBeTruthy();
    expect(screen.getByText('Chained step')).toBeTruthy();
  });

  it('given more usages than a page should page them', () => {
    // Arrange
    renderInApp(<PayloadUsagesTable usages={someUsages(manyUsages(12))} />);
    expect(screen.getByText('Inject 0')).toBeTruthy();
    expect(screen.queryByText('Inject 10')).toBeNull();

    // Act
    fireEvent.click(screen.getByRole('button', { name: /next page/i }));

    // Assert
    expect(screen.getByText('Inject 10')).toBeTruthy();
    expect(screen.queryByText('Inject 0')).toBeNull();
  });

  it.each([
    [1, '1 other inject you cannot see'],
    [4, '4 other injects you cannot see'],
  ])('given %i hidden usages should count them', (hidden, label) => {
    // Act
    renderInApp(<PayloadUsagesTable usages={someUsages([], hidden)} />);

    // Assert
    expect(screen.getByText(label)).toBeTruthy();
    expect(screen.queryByText('No inject uses this version')).toBeNull();
  });
});
