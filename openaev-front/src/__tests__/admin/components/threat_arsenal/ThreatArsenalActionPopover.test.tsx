import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalActionPopover from '../../../../admin/components/threat_arsenal/ThreatArsenalActionPopover';
import { anAction, renderInApp } from './threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  customize: vi.fn(),
  fetchAction: vi.fn(),
  fetchCustomization: vi.fn(),
}));

vi.mock('../../../../utils/utils', () => ({
  isFeatureEnabled: () => true,
  download: vi.fn(),
}));
vi.mock('../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));
vi.mock('../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  customizeThreatArsenalAction: mocks.customize,
  fetchThreatArsenalAction: mocks.fetchAction,
  fetchThreatArsenalCustomization: mocks.fetchCustomization,
  deleteThreatArsenalAction: vi.fn(),
  duplicateThreatArsenalAction: vi.fn(),
  exportThreatArsenalAction: vi.fn(),
  updateThreatArsenalAction: vi.fn(),
}));
vi.mock('../../../../admin/components/threat_arsenal/revocation/usePayloadRevocation', () => ({
  default: () => ({
    canRevoke: false,
    revocable: [],
    liftable: [],
    revocation: null,
    openRevocation: vi.fn(),
    closeRevocation: vi.fn(),
    revocationDone: vi.fn(),
  }),
}));
vi.mock('../../../../admin/components/threat_arsenal/revocation/PayloadRevocationDialog', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/versions/useNewVersionNotice', () => ({
  default: () => ({
    notice: null,
    afterSave: vi.fn(),
    closeNotice: vi.fn(),
  }),
}));
vi.mock('../../../../components/common/Drawer', () => ({
  default: ({ open, title, children }: {
    open: boolean;
    title: string;
    children: ReactNode;
  }) => (open ? <section aria-label={title}>{children}</section> : null),
}));
vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalActionForm', () => ({
  default: ({ filigranValues }: { filigranValues?: object }) => <form aria-label={filigranValues ? 'Customized copy form' : 'Action form'} />,
}));

vi.mock('../../../../admin/components/integrations/injectors/injector_contracts/InjectorContractForm', () => ({ default: () => null }));

// Radix opens its menu on `pointerdown`, and jsdom implements neither PointerEvent nor pointer capture.
const openMenu = () => {
  const trigger = screen.getByRole('button', { name: 'More actions' });
  fireEvent.pointerDown(trigger, {
    button: 0,
    ctrlKey: false,
  });
  fireEvent.click(trigger);
};

describe('ThreatArsenalActionPopover', () => {
  beforeEach(() => {
    mocks.fetchAction.mockResolvedValue({ data: { action_labels: { en: 'Mimikatz (customized)' } } });
    mocks.fetchCustomization.mockResolvedValue({ data: { customization_base_action_id: 'filigran-action' } });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('Customize', () => {
    it('given_aFiligranVersion_should_createTheCopyAndOpenItsEditDrawer', async () => {
      // Arrange
      const copy = anAction({
        injector_contract_id: 'copy-action',
        action_labels: { en: 'Mimikatz (customized)' },
        action_payload_customized: true,
        action_payload: { payload_id: 'copy-payload' },
      });
      mocks.customize.mockResolvedValue({ data: copy });
      const onDuplicate = vi.fn();
      renderInApp(<ThreatArsenalActionPopover action={anAction({ action_payload_managed: true })} onDuplicate={onDuplicate} />);
      openMenu();

      // Act
      fireEvent.click(screen.getByRole('menuitem', { name: 'Customize' }));

      // Assert
      await waitFor(() => expect(onDuplicate).toHaveBeenCalledWith(copy));
      expect(mocks.customize).toHaveBeenCalledWith('action-v2');
      expect(await screen.findByRole('region', { name: 'Customize · Mimikatz (customized)' })).toBeInTheDocument();
      expect(await screen.findByRole('form', { name: 'Customized copy form' })).toBeInTheDocument();
      expect(mocks.fetchAction).toHaveBeenCalledWith('copy-action');
    });

    it.each([
      ['a local action', anAction()],
      ['a revoked Filigran version', anAction({
        action_payload_managed: true,
        action_payload_revoked: true,
      })],
    ])('given_%s_should_notOfferIt', (_, action) => {
      // Arrange
      renderInApp(<ThreatArsenalActionPopover action={action} />);

      // Act
      openMenu();

      // Assert
      expect(screen.getByRole('menuitem', { name: 'Export' })).toBeInTheDocument();
      expect(screen.queryByRole('menuitem', { name: 'Customize' })).toBeNull();
    });
  });
});
