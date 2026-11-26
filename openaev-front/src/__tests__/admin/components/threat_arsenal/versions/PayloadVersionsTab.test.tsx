import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadVersionsTab from '../../../../../admin/components/threat_arsenal/versions/PayloadVersionsTab';
import { aUsage, aVersion, renderInApp, someUsages } from '../threatArsenalTestUtils';

const { keep, showAgain, can, changes } = vi.hoisted(() => ({
  keep: vi.fn(),
  showAgain: vi.fn(),
  can: vi.fn(() => false),
  // What v2 changed: its command.
  changes: vi.fn(() => Promise.resolve({
    data: [{
      change_field: 'command_content',
      change_before: 'mimikatz v1',
      change_after: 'mimikatz v2',
    }],
  })),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  keepThreatArsenalOlderVersions: keep,
  showThreatArsenalOlderVersions: showAgain,
  moveThreatArsenalUsages: vi.fn(),
  fetchThreatArsenalVersionChanges: changes,
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
const usages = {
  'payload-v2': someUsages([aUsage()], 1),
  'payload-v1': someUsages([aUsage({
    usage_id: 'usage-old',
    usage_name: 'Old inject',
  })]),
};

describe('PayloadVersionsTab', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    can.mockReturnValue(false);
  });

  it('given the versions still loading should show no version', () => {
    // Act
    renderInApp(<PayloadVersionsTab versions={null} usages={null} onMoved={vi.fn()} />);

    // Assert
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('given versions should count the injects of each and open the latest', () => {
    // Act
    renderInApp(<PayloadVersionsTab versions={[latest, older]} usages={usages} onMoved={vi.fn()} />);

    // Assert
    expect(screen.getByText('(2 injects)')).toBeTruthy();
    expect(screen.getByText('(1 inject)')).toBeTruthy();
    const [latestRow, olderRow] = screen.getAllByRole('button');
    expect(latestRow.getAttribute('aria-expanded')).toBe('true');
    expect(olderRow.getAttribute('aria-expanded')).toBe('false');
    expect(screen.getByText('Revoked')).toBeTruthy();
    expect(screen.getByText('These injects will fail. Move them to another version.')).toBeTruthy();
    expect(screen.getByText('Dump credentials')).toBeTruthy();
    expect(screen.getByText('1 other inject you cannot see')).toBeTruthy();
    expect(screen.queryByText('Old inject')).toBeNull();
  });

  it('given a folded version clicked should show its injects', () => {
    // Arrange
    renderInApp(<PayloadVersionsTab versions={[latest, older]} usages={usages} onMoved={vi.fn()} />);

    // Act
    fireEvent.click(screen.getByText('v1'));

    // Assert
    expect(screen.getByText('Old inject')).toBeTruthy();
    expect(screen.getAllByRole('button')[1].getAttribute('aria-expanded')).toBe('true');
  });

  it('given the open version clicked should fold it', () => {
    // Arrange
    renderInApp(<PayloadVersionsTab versions={[latest, older]} usages={usages} onMoved={vi.fn()} />);

    // Act
    fireEvent.click(screen.getByText('v2'));

    // Assert
    expect(screen.queryByText('Dump credentials')).toBeNull();
    expect(screen.queryByText('These injects will fail. Move them to another version.')).toBeNull();
  });

  it('given catalogue versions not installed should list them among the others, marked not installed', () => {
    // Act
    renderInApp(<PayloadVersionsTab versions={[latest, older]} usages={usages} onMoved={vi.fn()} notInstalled={['3', '1.5']} />);

    // Assert
    const versionLabels = screen.getAllByText(/^v\d/).map(label => label.textContent);
    expect(versionLabels).toEqual(['v3', 'v2', 'v1.5', 'v1']);
    expect(screen.getAllByText('Not installed')).toHaveLength(2);
    expect(screen.getAllByRole('button')).toHaveLength(2);
  });

  describe('injects on older versions', () => {
    const KEPT = 'Kept on their version. The card\'s banner returns with the next version.';
    const OLDER = '1 inject still uses v1. Move it below, or keep it where it is.';

    it('given injects on an older version should count them and keep them on their version', async () => {
      // Arrange
      can.mockReturnValue(true);
      keep.mockResolvedValue({});
      renderInApp(<PayloadVersionsTab versions={[latest, older]} usages={usages} onMoved={vi.fn()} />);
      expect(await screen.findByText(OLDER)).toBeTruthy();

      // Act
      fireEvent.click(screen.getByText('Keep it on v1'));

      // Assert
      expect(await screen.findByText(KEPT)).toBeTruthy();
      expect(keep).toHaveBeenCalledWith('action-v2');
    });

    it('given them kept should show the banner again', async () => {
      // Arrange
      can.mockReturnValue(true);
      showAgain.mockResolvedValue({});
      renderInApp(
        <PayloadVersionsTab
          versions={[{
            ...latest,
            payload_older_versions_kept: true,
          }, older]}
          usages={usages}
          onMoved={vi.fn()}
        />,
      );
      await screen.findByText(KEPT);

      // Act
      fireEvent.click(screen.getByText('Show the banner again'));

      // Assert
      expect(await screen.findByText(OLDER)).toBeTruthy();
      expect(showAgain).toHaveBeenCalledWith('action-v2');
    });

    it('given no right to manage should only say how many', async () => {
      // Act
      renderInApp(<PayloadVersionsTab versions={[latest, older]} usages={usages} onMoved={vi.fn()} />);

      // Assert
      expect(await screen.findByText(OLDER)).toBeTruthy();
      expect(screen.queryByText('Keep it on v1')).toBeNull();
    });

    it('given no inject on an older version should say nothing', async () => {
      // Act
      renderInApp(
        <PayloadVersionsTab
          versions={[latest, older]}
          usages={{
            'payload-v2': someUsages([aUsage()]),
            'payload-v1': someUsages([]),
          }}
          onMoved={vi.fn()}
        />,
      );

      // Assert
      expect(await screen.findByText('(0 injects)')).toBeTruthy();
      expect(screen.queryByText(OLDER)).toBeNull();
    });

    it('given opened to move them should open the older version with its injects ticked and fold the latest', async () => {
      // Arrange
      can.mockReturnValue(true);
      Element.prototype.scrollIntoView = vi.fn();

      // Act
      renderInApp(
        <PayloadVersionsTab
          versions={[{
            ...latest,
            payload_revoked: false,
          }, older]}
          usages={usages}
          onMoved={vi.fn()}
          focusMove
        />,
      );

      // Assert
      expect(await screen.findByText('Old inject')).toBeTruthy();
      expect(await screen.findByText('1 selected')).toBeTruthy();
      expect(screen.queryByText('Dump credentials')).toBeNull();
    });

    it('given opened right after a save should say what was saved and tick nothing', async () => {
      // Arrange
      can.mockReturnValue(true);
      Element.prototype.scrollIntoView = vi.fn();

      // Act
      renderInApp(
        <PayloadVersionsTab
          versions={[{
            ...latest,
            payload_revoked: false,
          }, older]}
          usages={usages}
          onMoved={vi.fn()}
          focusMove
          saved
        />,
      );

      // Assert
      expect(await screen.findByText(`Saved as v2. ${OLDER}`)).toBeTruthy();
      expect(await screen.findByText('Old inject')).toBeTruthy();
      expect(screen.queryByText('1 selected')).toBeNull();
      expect(screen.getByRole('button', { name: /What changed in v2/ })).toBeTruthy();
      expect(await screen.findByText('Before · v1')).toBeTruthy();
      expect(screen.getByText('After · v2')).toBeTruthy();
      expect(screen.getByRole('group', { name: /Command/ })).toBeTruthy();
    });
  });
});
