import { afterEach, describe, expect, it, vi } from 'vitest';

import fetchFiligranValues from '../../../../../admin/components/threat_arsenal/customization/filigranValues';

const mocks = vi.hoisted(() => ({
  fetchAction: vi.fn(),
  fetchCustomization: vi.fn(),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalAction: mocks.fetchAction,
  fetchThreatArsenalCustomization: mocks.fetchCustomization,
}));

describe('fetchFiligranValues', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given_aCustomizedCopy_should_readTheFormValuesOfItsFiligranVersion', async () => {
    // Arrange
    const customization = {
      customization_base_action_id: 'filigran-1.0.0',
      customization_base_version: '1.0.0',
    };
    mocks.fetchCustomization.mockResolvedValue({ data: customization });
    mocks.fetchAction.mockResolvedValue({
      data: {
        action_id: 'filigran-payload',
        command_content: 'whoami',
      },
    });

    // Act
    const result = await fetchFiligranValues('copy');

    // Assert
    expect(mocks.fetchCustomization).toHaveBeenCalledWith('copy');
    expect(mocks.fetchAction).toHaveBeenCalledWith('filigran-1.0.0');
    expect(result.customization).toEqual(customization);
    expect(result.values).toMatchObject({ command_content: 'whoami' });
  });
});
