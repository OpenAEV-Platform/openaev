import '@testing-library/jest-dom/vitest';

import { act, cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalInformationDrawer from '../../../../../admin/components/threat_arsenal/ThreatArsenalInformationDrawer';
import { anAction, aUsage, aVersion, renderInApp, someUsages } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  versions: vi.fn(),
  counts: vi.fn(),
  usages: vi.fn(),
}));

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => false }) }));
vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalUsageCounts: mocks.counts,
  fetchThreatArsenalUsages: mocks.usages,
}));
vi.mock('../../../../../admin/components/threat_arsenal/versions/usePayloadVersions', () => ({ default: mocks.versions }));
vi.mock('../../../../../admin/components/threat_arsenal/customization/useCustomization', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/threat_arsenal/catalog/useCatalogPayloadVersions', () => ({ default: () => [] }));
vi.mock('../../../../../admin/components/threat_arsenal/hook/useActionDetails', () => ({
  default: () => ({
    details: null,
    loading: false,
  }),
}));
vi.mock('../../../../../admin/components/threat_arsenal/customization/PayloadUpdateBanner', () => ({ default: () => null }));
vi.mock('../../../../../components/common/Drawer', () => ({ default: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock('../../../../../admin/components/threat_arsenal/ThreatArsenalActionOverview', () => ({ default: ({ toMove }: { toMove?: number }) => <div data-testid="overview">{`${toMove}`}</div> }));

// Six versions, the latest first: v6 runs once, each older one twice.
const versions = [6, 5, 4, 3, 2, 1].map(number => aVersion({
  payload_id: `payload-v${number}`,
  payload_action_id: `action-v${number}`,
  payload_version: String(number),
  payload_is_latest: number === 6,
}));

const switchTo = (tab: string) => act(() => {
  fireEvent.mouseDown(screen.getByRole('tab', { name: tab }), { button: 0 });
});

describe('useActionDrawer', () => {
  beforeEach(() => {
    mocks.versions.mockReturnValue(versions);
    mocks.counts.mockResolvedValue({
      data: versions.map(version => ({
        payload_id: version.payload_id,
        payload_usage_count: version.payload_is_latest ? 1 : 2,
      })),
    });
    mocks.usages.mockImplementation((actionId: string) => Promise.resolve({ data: someUsages([aUsage({ usage_name: `Inject on ${actionId}` })]) }));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given six versions should count their usages in one call and load only the open version', async () => {
    // Act
    renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={anAction({ injector_contract_id: 'action-v6' })} initialTab="versions" />);

    // Assert
    expect(await screen.findAllByText('(2 injects)')).toHaveLength(5);
    expect(screen.getByText('(1 inject)')).toBeInTheDocument();
    expect(await screen.findByText('Inject on action-v6')).toBeInTheDocument();
    expect(mocks.counts).toHaveBeenCalledTimes(1);
    expect(mocks.counts).toHaveBeenCalledWith('action-v6');
    expect(mocks.usages).toHaveBeenCalledTimes(1);
  });

  it('given a version opened should load its usages only then, and keep them across tabs', async () => {
    // Arrange
    renderInApp(<ThreatArsenalInformationDrawer open onClose={vi.fn()} threatArsenalAction={anAction({ injector_contract_id: 'action-v6' })} initialTab="versions" />);
    await screen.findByText('Inject on action-v6');

    // Act
    fireEvent.click(screen.getByText('v3'));

    // Assert
    expect(await screen.findByText('Inject on action-v3')).toBeInTheDocument();
    expect(mocks.usages).toHaveBeenCalledTimes(2);
    expect(mocks.usages).toHaveBeenLastCalledWith('action-v3');

    // Act
    switchTo('Overview');
    expect(await screen.findByTestId('overview')).toHaveTextContent('10');
    switchTo('Versions');

    // Assert
    expect(await screen.findByText('Inject on action-v6')).toBeInTheDocument();
    await waitFor(() => expect(mocks.counts).toHaveBeenCalledTimes(1));
    expect(mocks.usages).toHaveBeenCalledTimes(2);
  });
});
