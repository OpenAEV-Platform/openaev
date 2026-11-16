import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalRunVersionDialog from '../../../../../admin/components/threat_arsenal/bulk/ThreatArsenalRunVersionDialog';
import { type PayloadVersionOutput, type ThreatArsenalAction } from '../../../../../utils/api-types';

const mockFetchVersions = vi.fn();
vi.mock('../../../../../actions/threat_arsenals/threatArsenal-version-actions', () => ({ fetchThreatArsenalVersions: (...args: unknown[]) => mockFetchVersions(...args) }));
vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    tPick: (labels?: Record<string, string>) => labels?.en ?? '',
  }),
}));

const action: ThreatArsenalAction = {
  injector_contract_id: 'v3',
  injector_contract_updated_at: '',
  action_attack_patterns_ids: [],
  action_domains_ids: [],
  action_labels: { en: 'Whoami' },
  action_payload: {
    payload_id: 'payload-v3',
    payload_version: '3.0.0',
    payload_approval_status: 'PENDING',
  },
};

const version = (id: string, number: string, status: PayloadVersionOutput['payload_approval_status']): PayloadVersionOutput => ({
  payload_id: `payload-${id}`,
  payload_action_id: id,
  payload_version: number,
  payload_installed: true,
  payload_revoked: false,
  payload_approval_status: status,
  payload_usage_count: 0,
});

describe('ThreatArsenalRunVersionDialog', () => {
  beforeEach(() => mockFetchVersions.mockReset());
  afterEach(cleanup);

  it('given a single approved version should run it without asking', async () => {
    // Arrange
    mockFetchVersions.mockResolvedValue({ data: [version('v1', '1.0.0', 'APPROVED')] });
    const onChoose = vi.fn();

    // Act
    render(<ThreatArsenalRunVersionDialog action={action} onClose={vi.fn()} onChoose={onChoose} />);

    // Assert
    await waitFor(() => expect(onChoose).toHaveBeenCalled());
    expect(onChoose.mock.calls[0][0].injector_contract_id).toBe('v1');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('given several versions should offer the latest approved one, not the pending one', async () => {
    // Arrange: newest first, as the API lists them
    mockFetchVersions.mockResolvedValue({
      data: [
        version('v3', '3.0.0', 'PENDING'),
        version('v2', '2.0.0', 'APPROVED'),
        version('v1', '1.0.0', 'APPROVED'),
      ],
    });
    const onChoose = vi.fn();
    render(<ThreatArsenalRunVersionDialog action={action} onClose={vi.fn()} onChoose={onChoose} />);

    // Act
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }));

    // Assert
    expect(screen.getByRole('combobox').textContent).toContain('v2.0.0');
    expect(onChoose.mock.calls[0][0].injector_contract_id).toBe('v2');
  });

  it('given the version shown in the drawer is approved should offer it first', async () => {
    // Arrange
    mockFetchVersions.mockResolvedValue({
      data: [
        version('v3', '3.0.0', 'PENDING'),
        version('v2', '2.0.0', 'APPROVED'),
        version('v1', '1.0.0', 'APPROVED'),
      ],
    });
    const onChoose = vi.fn();
    render(<ThreatArsenalRunVersionDialog action={action} preferredActionId="v1" onClose={vi.fn()} onChoose={onChoose} />);

    // Act
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }));

    // Assert
    expect(onChoose.mock.calls[0][0].injector_contract_id).toBe('v1');
  });

  it('given no approved version should warn and run nothing', async () => {
    // Arrange
    mockFetchVersions.mockResolvedValue({
      data: [
        version('v3', '3.0.0', 'PENDING'),
        version('v1', '1.0.0', 'REJECTED'),
      ],
    });
    const onChoose = vi.fn();

    // Act
    render(<ThreatArsenalRunVersionDialog action={action} onClose={vi.fn()} onChoose={onChoose} />);

    // Assert
    expect(await screen.findByText('This action has no approved version, so it cannot run a test.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Continue' })).toBeNull();
    expect(onChoose).not.toHaveBeenCalled();
  });

  it('given an action without payload should run it as it is', () => {
    // Arrange
    const onChoose = vi.fn();
    const injectorAction = {
      ...action,
      action_payload: undefined,
    };

    // Act
    render(<ThreatArsenalRunVersionDialog action={injectorAction} onClose={vi.fn()} onChoose={onChoose} />);

    // Assert
    expect(onChoose).toHaveBeenCalledWith(injectorAction);
    expect(mockFetchVersions).not.toHaveBeenCalled();
  });
});
