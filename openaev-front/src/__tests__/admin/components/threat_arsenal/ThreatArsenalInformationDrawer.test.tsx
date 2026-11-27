import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalInformationDrawer from '../../../../admin/components/threat_arsenal/ThreatArsenalInformationDrawer';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import { anAction, aVersion, renderInApp } from './threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  can: vi.fn(),
  versions: vi.fn(),
  counts: vi.fn(),
  usages: vi.fn(),
  customization: vi.fn(),
  catalogue: vi.fn(),
  details: vi.fn(),
  install: vi.fn(),
}));

vi.mock('../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));
vi.mock('../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: mocks.can }) }));
vi.mock('../../../../admin/components/threat_arsenal/versions/usePayloadVersions', () => ({ default: mocks.versions }));
vi.mock('../../../../admin/components/threat_arsenal/versions/usages/useUsageCounts', () => ({ default: mocks.counts }));
vi.mock('../../../../admin/components/threat_arsenal/versions/usages/useUsagesByVersion', () => ({ default: mocks.usages }));
vi.mock('../../../../admin/components/threat_arsenal/customization/useCustomization', () => ({ default: mocks.customization }));
vi.mock('../../../../admin/components/threat_arsenal/catalog/useCatalogPayloadVersions', () => ({ default: mocks.catalogue }));
vi.mock('../../../../admin/components/threat_arsenal/hook/useActionDetails', () => ({ default: mocks.details }));
vi.mock('../../../../actions/catalog/payload-catalog-actions', () => ({ installCatalogPayload: mocks.install }));
vi.mock('../../../../admin/components/threat_arsenal/customization/PayloadUpdateBanner', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/catalog/CatalogPayloadVersionPreview', () => ({ default: () => null }));
vi.mock('../../../../components/common/Drawer', () => ({
  default: ({ children, headerActions }: {
    children: ReactNode;
    headerActions?: ReactNode;
  }) => (
    <div>
      <header>{headerActions}</header>
      {children}
    </div>
  ),
}));
vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalActionOverview', () => ({
  default: ({ action, toMove, newBase }: {
    action: { injector_contract_id: string };
    toMove?: number;
    newBase?: string;
  }) => <div data-testid="overview">{`${action.injector_contract_id} ${toMove} ${newBase}`}</div>,
}));

const latest = aVersion({
  payload_id: 'payload-v2',
  payload_action_id: 'action-v2',
  payload_version: '2',
  payload_is_latest: true,
});
const older = aVersion();

const loaded = (versions: PayloadVersionOutput[], counts: Record<string, number>) => {
  mocks.versions.mockReturnValue(versions);
  mocks.counts.mockReturnValue(counts);
  mocks.usages.mockReturnValue({});
};

describe('ThreatArsenalInformationDrawer', () => {
  beforeEach(() => {
    mocks.can.mockReturnValue(true);
    mocks.customization.mockReturnValue(null);
    mocks.catalogue.mockReturnValue([]);
    mocks.details.mockReturnValue({
      details: null,
      loading: false,
    });
    loaded([latest, older], {
      'payload-v2': 1,
      'payload-v1': 3,
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('Run a test', () => {
    it('given the right to run tests should run one on the shown action', () => {
      // Arrange
      const onRunTest = vi.fn();
      const action = anAction();
      renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={action} onRunTest={onRunTest} />);

      // Act
      fireEvent.click(screen.getByRole('button', { name: 'Run a test' }));

      // Assert
      expect(onRunTest).toHaveBeenCalledWith(action);
    });

    it.each([
      ['a revoked version', true, true],
      ['no right to run tests', false, false],
    ])('given %s should offer no test', (_case, revoked, canRun) => {
      // Arrange
      mocks.can.mockReturnValue(canRun);

      // Act
      renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={anAction({ action_payload_revoked: revoked })} onRunTest={vi.fn()} />);

      // Assert
      expect(screen.queryByRole('button', { name: 'Run a test' })).toBeNull();
    });
  });

  describe('Overview', () => {
    it('given usages on older versions and a newer Filigran base should pass both to the trust line', () => {
      // Arrange
      mocks.customization.mockReturnValue({
        customization_base_action_id: 'base-v1',
        customization_base_version: '1',
        customization_update: { update_version: '3' },
      });

      // Act
      renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={anAction({ action_payload_customized: true })} />);

      // Assert
      expect(screen.getByTestId('overview').textContent).toBe('action-v2 3 3');
    });

    it('given the versions loaded should offer them next to the tabs, the opened one picked', () => {
      // Act
      renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={anAction()} />);

      // Assert
      expect(screen.getByRole('combobox', { name: 'Version' }).textContent).toBe('v2 · Latest version');
    });

    it('given an action without payload should show no version', () => {
      // Arrange
      loaded([], {});

      // Act
      renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={anAction({ action_payload: undefined })} />);

      // Assert
      expect(screen.queryByRole('combobox', { name: 'Version' })).toBeNull();
      expect(screen.getByTestId('overview').textContent).toBe('action-v2 0 undefined');
    });
  });

  describe('Catalogue version not installed', () => {
    it('given the right to install should install it from its row', async () => {
      // Arrange
      mocks.details.mockReturnValue({
        details: { action_external_id: 'mimikatz' },
        loading: false,
      });
      mocks.catalogue.mockReturnValue([{
        catalog_payload_external_id: 'mimikatz',
        catalog_payload_version: '3',
      }]);
      mocks.install.mockResolvedValue({ installed_id: 'payload-v3' });
      renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={anAction({ action_payload_managed: true })} initialTab="versions" />);

      // Act
      fireEvent.click(screen.getByRole('button', { name: 'Install' }));

      // Assert
      await waitFor(() => expect(mocks.install).toHaveBeenCalledWith('mimikatz', '3'));
      await waitFor(() => expect(mocks.versions).toHaveBeenCalledWith('action-v2', true, 1));
    });

    it('given no right to install should only tell it is not installed', () => {
      // Arrange
      mocks.can.mockReturnValue(false);
      mocks.catalogue.mockReturnValue([{
        catalog_payload_external_id: 'mimikatz',
        catalog_payload_version: '3',
      }]);

      // Act
      renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={anAction({ action_payload_managed: true })} initialTab="versions" />);

      // Assert
      expect(screen.getByText('Not installed')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Install' })).toBeNull();
    });
  });
});
