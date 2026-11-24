import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadContractVersionSelect from '../../../../../admin/components/threat_arsenal/versions/PayloadContractVersionSelect';
import { type InjectorContract, type PayloadVersionOutput } from '../../../../../utils/api-types';

const { mockFetchVersions, mockFetchContract, mockFeatureEnabled } = vi.hoisted(() => ({
  mockFetchVersions: vi.fn(),
  mockFetchContract: vi.fn(),
  mockFeatureEnabled: vi.fn(() => true),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalVersions: mockFetchVersions }));

vi.mock('../../../../../actions/InjectorContracts', () => ({ directFetchInjectorContract: mockFetchContract }));

vi.mock('../../../../../utils/utils', async importOriginal => ({
  ...(await importOriginal() as Record<string, unknown>),
  isFeatureEnabled: mockFeatureEnabled,
}));

vi.mock('../../../../../admin/components/threat_arsenal/versions/PayloadVersionSelect', () => ({
  default: ({ value, versions, onChange }: {
    value: string;
    versions: PayloadVersionOutput[];
    onChange: (payloadId: string) => void;
  }) => (
    <select aria-label="Version" value={value} onChange={event => onChange(event.target.value)}>
      {versions.map(version => <option key={version.payload_id} value={version.payload_id}>{version.payload_version}</option>)}
    </select>
  ),
}));

const version = (id: string, revoked = false): PayloadVersionOutput => ({
  payload_id: `payload-${id}`,
  payload_action_id: `action-${id}`,
  payload_version: id,
  payload_is_latest: false,
  payload_revoked: revoked,
  payload_revoked_by_catalogue: false,
} as PayloadVersionOutput);

const action = {
  injector_contract_id: 'action-1.0.0',
  injector_contract_payload: { payload_id: 'payload-1.0.0' },
} as unknown as InjectorContract;

const renderSelect = (onChange = vi.fn(), value = 'action-1.0.0') => render(
  <PayloadContractVersionSelect action={action} value={value} onChange={onChange} />,
);

describe('PayloadContractVersionSelect', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given the catalogue preview is off, should render nothing and fetch nothing', () => {
    // Arrange
    mockFeatureEnabled.mockReturnValueOnce(false);

    // Act
    renderSelect();

    // Assert
    expect(screen.queryByLabelText('Version')).toBeNull();
    expect(mockFetchVersions).not.toHaveBeenCalled();
  });

  it('given an action without payload, should render nothing and fetch nothing', () => {
    // Act
    render(<PayloadContractVersionSelect action={{ injector_contract_id: 'action-1.0.0' }} value="action-1.0.0" onChange={vi.fn()} />);

    // Assert
    expect(screen.queryByLabelText('Version')).toBeNull();
    expect(mockFetchVersions).not.toHaveBeenCalled();
  });

  it('given revoked versions, should list only the one in use and the unrevoked ones', async () => {
    // Arrange
    mockFetchVersions.mockResolvedValue({ data: [version('1.2.0'), version('1.1.0', true), version('1.0.0', true)] });

    // Act
    renderSelect();

    // Assert
    await waitFor(() => expect(screen.getByLabelText('Version')).toBeDefined());
    expect(mockFetchVersions).toHaveBeenCalledWith('action-1.0.0', false);
    expect(screen.getAllByRole<HTMLOptionElement>('option').map(option => option.value)).toEqual(['payload-1.2.0', 'payload-1.0.0']);
  });

  it('given a single usable version, should render nothing', async () => {
    // Arrange
    mockFetchVersions.mockResolvedValue({ data: [version('1.1.0', true), version('1.0.0')] });

    // Act
    renderSelect();

    // Assert
    await waitFor(() => expect(mockFetchVersions).toHaveBeenCalled());
    expect(screen.queryByLabelText('Version')).toBeNull();
  });

  it('given another version chosen, should hand over its action', async () => {
    // Arrange
    const onChange = vi.fn();
    const chosen = { injector_contract_id: 'action-1.1.0' };
    mockFetchVersions.mockResolvedValue({ data: [version('1.1.0'), version('1.0.0')] });
    mockFetchContract.mockResolvedValue({ data: chosen });
    renderSelect(onChange);
    await waitFor(() => expect(screen.getByLabelText('Version')).toBeDefined());

    // Act
    fireEvent.change(screen.getByLabelText('Version'), { target: { value: 'payload-1.1.0' } });

    // Assert
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(chosen));
    expect(mockFetchContract).toHaveBeenCalledWith('action-1.1.0');
  });
});
