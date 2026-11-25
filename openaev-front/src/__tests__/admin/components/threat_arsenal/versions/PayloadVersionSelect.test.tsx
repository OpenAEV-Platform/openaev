import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadVersionSelect from '../../../../../admin/components/threat_arsenal/versions/PayloadVersionSelect';
import { aVersion, renderInApp } from '../threatArsenalTestUtils';

const latest = aVersion({
  payload_id: 'payload-v2',
  payload_version: '2',
  payload_is_latest: true,
});
const revoked = aVersion({ payload_revoked: true });

describe('PayloadVersionSelect', () => {
  afterEach(cleanup);

  it.each([
    ['the latest version', 'payload-v2', 'v2 · Latest version'],
    ['a revoked version', 'payload-v1', 'v1 · Revoked'],
    ['a catalogue version not installed', '3', 'v3 · Not installed'],
  ])('given %s picked should label it', (_case, value, label) => {
    // Act
    renderInApp(<PayloadVersionSelect value={value} versions={[latest, revoked]} notInstalled={['3']} onChange={vi.fn()} />);

    // Assert
    expect(screen.getByRole('combobox', { name: 'Version' }).textContent).toBe(label);
  });
});
