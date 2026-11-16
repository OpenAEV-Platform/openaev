import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadVersionComparison from '../../../../../admin/components/threat_arsenal/approval/PayloadVersionComparison';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

const runs = {
  content: 'whoami\nhostname',
  executor: 'bash',
  platforms: ['Linux'],
};

describe('PayloadVersionComparison', () => {
  afterEach(cleanup);

  describe('a new action, with no approved version to compare with', () => {
    it('given no earlier version should show every field it runs as added lines', () => {
      // Arrange / Act
      render(<PayloadVersionComparison pending={runs} pendingNumber="1.0.0" />);

      // Assert
      expect(screen.getByText('What this version runs')).toBeTruthy();
      expect(screen.getByText('New action: nothing was approved before, so everything below is new in v{version}.')).toBeTruthy();
      // Each field is a code diff: its group is labelled with its line counts, nothing removed.
      const groups = screen.getAllByRole('group');
      expect(groups.length).toBe(3);
      groups.forEach(group => expect(group.getAttribute('aria-label')).toContain('{added} lines added, {removed} lines removed'));
      expect(screen.getByText('whoami')).toBeTruthy();
      expect(screen.getByText('hostname')).toBeTruthy();
      // No Before: the Before / After key is not shown.
      expect(screen.queryByText(/Before ·/)).toBeNull();
    });
  });

  describe('a new version of an approved action', () => {
    it('given an earlier version should only show the changed fields, with the Before / After key', () => {
      // Arrange / Act
      render(
        <PayloadVersionComparison
          active={{
            ...runs,
            content: 'whoami',
          }}
          pending={runs}
          activeNumber="1.0.0"
          pendingNumber="1.1.0"
        />,
      );

      // Assert
      expect(screen.getByText('What changed')).toBeTruthy();
      expect(screen.getAllByRole('group').length).toBe(1);
      expect(screen.getAllByText(/Before ·/).length).toBeGreaterThan(0);
    });
  });
});
