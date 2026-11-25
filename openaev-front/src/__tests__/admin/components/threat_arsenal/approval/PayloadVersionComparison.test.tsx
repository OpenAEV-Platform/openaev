import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadVersionComparison from '../../../../../admin/components/threat_arsenal/approval/PayloadVersionComparison';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

describe('PayloadVersionComparison', () => {
  afterEach(cleanup);

  describe('a new action, with no approved version to compare with', () => {
    it('given no earlier version should show every field it has as added lines', () => {
      // Arrange / Act
      render(
        <PayloadVersionComparison
          isNew
          changes={[
            {
              field: 'command_content',
              before: null,
              after: 'whoami\nhostname',
            },
            {
              field: 'command_executor',
              before: null,
              after: 'bash',
            },
            {
              field: 'payload_platforms',
              before: null,
              after: ['Linux'],
            },
          ]}
          pendingNumber="1.0.0"
        />,
      );

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
    it('given a name change only should show the Name diff', () => {
      // Arrange / Act
      render(
        <PayloadVersionComparison
          changes={[{
            field: 'payload_name',
            before: 'Whoami',
            after: 'Whoami as root',
          }]}
          activeNumber="1.0.0"
          pendingNumber="1.0.1"
        />,
      );

      // Assert
      expect(screen.getByText('What changed')).toBeTruthy();
      expect(screen.getAllByText('Name').length).toBeGreaterThan(0);
      expect(screen.getAllByText(/Before ·/).length).toBeGreaterThan(0);
    });

    it('given a description change only should show the Description diff', () => {
      // Arrange / Act
      render(
        <PayloadVersionComparison
          changes={[{
            field: 'payload_description',
            before: 'Prints the user',
            after: 'Prints who runs the agent',
          }]}
          activeNumber="1.0.0"
          pendingNumber="1.0.1"
        />,
      );

      // Assert
      expect(screen.getAllByText('Description').length).toBeGreaterThan(0);
    });

    it('given attack patterns and expectations changed should show both, one item per line', () => {
      // Arrange / Act
      render(
        <PayloadVersionComparison
          changes={[
            {
              field: 'payload_attack_patterns',
              before: ['T1033 System Owner/User Discovery'],
              after: ['T1033 System Owner/User Discovery', 'T1059 Command and Scripting Interpreter'],
            },
            {
              field: 'payload_expectations',
              before: ['PREVENTION'],
              after: ['DETECTION', 'PREVENTION'],
            },
          ]}
          activeNumber="1.0.0"
          pendingNumber="1.1.0"
        />,
      );

      // Assert
      expect(screen.getAllByText('Attack patterns').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Expectations').length).toBeGreaterThan(0);
      expect(screen.getByText('T1059 Command and Scripting Interpreter')).toBeTruthy();
      expect(screen.getByText('DETECTION')).toBeTruthy();
    });

    it('given nothing changed should show no message, only the heading', () => {
      // Arrange / Act
      render(<PayloadVersionComparison changes={[]} activeNumber="1.0.0" pendingNumber="1.0.0" />);

      // Assert
      expect(screen.getByText('What changed')).toBeTruthy();
      expect(screen.queryByText('No change in what the action runs.')).toBeNull();
      expect(screen.queryAllByRole('group')).toHaveLength(0);
    });
  });

  describe('a draft', () => {
    it('given a draft that renames the action should show the Name diff against its source version', () => {
      // Arrange / Act
      render(
        <PayloadVersionComparison
          draft
          title="What this draft changes"
          changes={[{
            field: 'payload_name',
            before: 'Whoami',
            after: 'Whoami draft',
          }]}
          activeNumber="1.0.0"
          pendingNumber="1.0.0"
        />,
      );

      // Assert
      expect(screen.getByText('What this draft changes')).toBeTruthy();
      expect(screen.getAllByText('Name').length).toBeGreaterThan(0);
      expect(screen.getAllByText('After · draft').length).toBeGreaterThan(0);
    });
  });
});
