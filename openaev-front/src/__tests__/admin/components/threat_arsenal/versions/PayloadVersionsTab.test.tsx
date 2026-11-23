import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadVersionsTab from '../../../../../admin/components/threat_arsenal/versions/PayloadVersionsTab';
import { aUsage, aVersion, renderInApp, someUsages } from '../threatArsenalTestUtils';

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => false }) }));

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
  afterEach(cleanup);

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
});
