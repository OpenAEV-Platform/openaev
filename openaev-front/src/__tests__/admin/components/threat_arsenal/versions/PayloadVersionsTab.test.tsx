import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PayloadVersionsTab from '../../../../../admin/components/threat_arsenal/versions/PayloadVersionsTab';
import { aUsage, aVersion, renderInApp, someUsages } from '../threatArsenalTestUtils';

const { fetchUsages, keep, showAgain, can } = vi.hoisted(() => ({
  fetchUsages: vi.fn(),
  keep: vi.fn(),
  showAgain: vi.fn(),
  can: vi.fn(() => false),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalUsages: fetchUsages,
  keepThreatArsenalOlderVersions: keep,
  showThreatArsenalOlderVersions: showAgain,
  moveThreatArsenalUsages: vi.fn(),
}));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can }) }));

const latest = aVersion({
  payload_id: 'payload-v2',
  payload_action_id: 'action-v2',
  payload_version: '2',
  payload_is_latest: true,
  payload_revoked: true,
});
const older = aVersion();

describe('PayloadVersionsTab', () => {
  beforeEach(() => {
    fetchUsages.mockImplementation((actionId: string) => Promise.resolve({
      data: actionId === 'action-v2'
        ? someUsages([aUsage()], 1)
        : someUsages([aUsage({
            usage_id: 'usage-old',
            usage_name: 'Old inject',
          })]),
    }));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    can.mockReturnValue(false);
  });

  it('given the versions still loading should show no version', () => {
    // Act
    renderInApp(<PayloadVersionsTab versions={null} />);

    // Assert
    expect(screen.queryByRole('button')).toBeNull();
    expect(fetchUsages).not.toHaveBeenCalled();
  });

  it('given versions should count the injects of each and open the latest', async () => {
    // Act
    renderInApp(<PayloadVersionsTab versions={[latest, older]} />);

    // Assert
    expect(await screen.findByText('(2 injects)')).toBeTruthy();
    expect(await screen.findByText('(1 inject)')).toBeTruthy();
    const [latestRow, olderRow] = screen.getAllByRole('button');
    expect(latestRow.getAttribute('aria-expanded')).toBe('true');
    expect(olderRow.getAttribute('aria-expanded')).toBe('false');
    expect(screen.getByText('Revoked')).toBeTruthy();
    expect(screen.getByText('These injects will fail. Move them to another version.')).toBeTruthy();
    expect(screen.getByText('Dump credentials')).toBeTruthy();
    expect(screen.getByText('1 other inject you cannot see')).toBeTruthy();
    expect(screen.queryByText('Old inject')).toBeNull();
  });

  it('given a folded version clicked should show its injects', async () => {
    // Arrange
    renderInApp(<PayloadVersionsTab versions={[latest, older]} />);
    await screen.findByText('(1 inject)');

    // Act
    fireEvent.click(screen.getByText('v1'));

    // Assert
    await waitFor(() => expect(screen.getByText('Old inject')).toBeTruthy());
    expect(screen.getAllByRole('button')[1].getAttribute('aria-expanded')).toBe('true');
  });

  it('given the open version clicked should fold it', async () => {
    // Arrange
    renderInApp(<PayloadVersionsTab versions={[latest, older]} />);
    await screen.findByText('Dump credentials');

    // Act
    fireEvent.click(screen.getByText('v2'));

    // Assert
    expect(screen.queryByText('Dump credentials')).toBeNull();
    expect(screen.queryByText('These injects will fail. Move them to another version.')).toBeNull();
  });

  describe('injects on older versions', () => {
    const KEPT = 'Kept on their version. The card's banner returns with the next version.';

    it('given injects on an older version should count them and keep them on their version', async () => {
      // Arrange
      can.mockReturnValue(true);
      keep.mockResolvedValue({});
      renderInApp(<PayloadVersionsTab versions={[latest, older]} />);
      expect(await screen.findByText('1 inject uses an older version')).toBeTruthy();
      expect(screen.getByText('Move them below, or keep them where they are.')).toBeTruthy();

      // Act
      fireEvent.click(screen.getByText('Keep them on their version'));

      // Assert
      expect(await screen.findByText(KEPT)).toBeTruthy();
      expect(keep).toHaveBeenCalledWith('action-v2');
    });

    it('given them kept should show the banner again', async () => {
      // Arrange
      can.mockReturnValue(true);
      showAgain.mockResolvedValue({});
      renderInApp(<PayloadVersionsTab versions={[{
        ...latest,
        payload_older_versions_kept: true,
      }, older]}
      />);
      await screen.findByText(KEPT);

      // Act
      fireEvent.click(screen.getByText('Show the banner again'));

      // Assert
      expect(await screen.findByText('1 inject uses an older version')).toBeTruthy();
      expect(showAgain).toHaveBeenCalledWith('action-v2');
    });

    it('given no right to manage should only say how many', async () => {
      // Act
      renderInApp(<PayloadVersionsTab versions={[latest, older]} />);

      // Assert
      expect(await screen.findByText('1 inject uses an older version')).toBeTruthy();
      expect(screen.queryByText('Keep them on their version')).toBeNull();
    });

    it('given no inject on an older version should say nothing', async () => {
      // Arrange
      fetchUsages.mockImplementation((actionId: string) => Promise.resolve({ data: actionId === 'action-v2' ? someUsages([aUsage()]) : someUsages([]) }));

      // Act
      renderInApp(<PayloadVersionsTab versions={[latest, older]} />);

      // Assert
      expect(await screen.findByText('(0 injects)')).toBeTruthy();
      expect(screen.queryByText('1 inject uses an older version')).toBeNull();
    });
  });
});
