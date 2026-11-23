import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadUpdateBanner from '../../../../../admin/components/threat_arsenal/customization/PayloadUpdateBanner';
import { type PayloadCustomizationOutput } from '../../../../../utils/api-types';

const { APPLY } = vi.hoisted(() => ({ APPLY: 'Apply' }));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (message: string, values?: Record<string, string>) => message.replace(/\{(\w+)\}/g, (_, key: string) => values?.[key] ?? key) }) }));
vi.mock('../../../../../admin/components/threat_arsenal/customization/PayloadUpdateDialog', () => ({
  default: ({ onUpdated }: { onUpdated: (action: { injector_contract_id: string }) => void }) => (
    <div role="dialog">
      <button type="button" onClick={() => onUpdated({ injector_contract_id: 'copy-1.1.0' })}>{APPLY}</button>
    </div>
  ),
}));

const customization = (update?: PayloadCustomizationOutput['customization_update']): PayloadCustomizationOutput => ({
  customization_base_action_id: 'filigran-1.0.0',
  customization_base_version: '1.0.0',
  customization_update: update,
});

const newer: PayloadCustomizationOutput['customization_update'] = {
  update_payload_id: 'filigran-1.1.0',
  update_version: '1.1.0',
  update_fields: [],
  update_release_notes: [],
};

describe('PayloadUpdateBanner', () => {
  afterEach(cleanup);

  it('given_aNewerFiligranVersion_should_offerItAndOpenTheReview', () => {
    // Arrange
    const onUpdated = vi.fn();
    render(<PayloadUpdateBanner actionId="copy" customization={customization(newer)} canUpdate onUpdated={onUpdated} />);

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Update' }));
    fireEvent.click(screen.getByRole('button', { name: APPLY }));

    // Assert
    expect(screen.getByText('Filigran v1.1.0 is available')).toBeInTheDocument();
    expect(screen.getByText('Based on v1.0.0')).toBeInTheDocument();
    expect(onUpdated).toHaveBeenCalledWith({ injector_contract_id: 'copy-1.1.0' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('given_noRightToUpdate_should_onlyTellTheUpdate', () => {
    // Act
    render(<PayloadUpdateBanner actionId="copy" customization={customization(newer)} canUpdate={false} onUpdated={vi.fn()} />);

    // Assert
    expect(screen.getByText('Filigran v1.1.0 is available')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Update' })).not.toBeInTheDocument();
  });

  it.each([
    ['no newer version', customization()],
    ['an action that is not a copy', null],
  ])('given_%s_should_showNothing', (_, value) => {
    // Act
    const { container } = render(<PayloadUpdateBanner actionId="copy" customization={value} canUpdate onUpdated={vi.fn()} />);

    // Assert
    expect(container).toBeEmptyDOMElement();
  });
});
