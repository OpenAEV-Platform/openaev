import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalRunBlockedDialog from '../../../../../admin/components/threat_arsenal/bulk/ThreatArsenalRunBlockedDialog';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    tPick: (labels?: Record<string, string>) => labels?.en ?? '',
  }),
}));

const blocked: ThreatArsenalAction[] = [{
  injector_contract_id: 'new',
  injector_contract_updated_at: '',
  action_attack_patterns_ids: [],
  action_domains_ids: [],
  action_labels: { en: 'Never approved action' },
}];

describe('ThreatArsenalRunBlockedDialog', () => {
  afterEach(cleanup);

  it('given other actions can run should name the blocked ones and offer to continue', () => {
    // Arrange
    const onContinue = vi.fn();
    render(<ThreatArsenalRunBlockedDialog blocked={blocked} runnableCount={2} onClose={vi.fn()} onContinue={onContinue} />);

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));

    // Assert
    expect(screen.getByText('Never approved action')).toBeTruthy();
    expect(onContinue).toHaveBeenCalled();
  });

  it('given no action can run should only offer to close', () => {
    // Arrange / Act
    render(<ThreatArsenalRunBlockedDialog blocked={blocked} runnableCount={0} onClose={vi.fn()} onContinue={vi.fn()} />);

    // Assert
    expect(screen.queryByRole('button', { name: 'Continue' })).toBeNull();
    expect(screen.getAllByRole('button', { name: 'Close' }).length).toBeGreaterThan(0);
  });
});
