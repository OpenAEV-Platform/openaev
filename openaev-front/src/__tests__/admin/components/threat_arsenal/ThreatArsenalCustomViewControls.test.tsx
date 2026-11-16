import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type CustomView } from '../../../../admin/components/threat_arsenal/customViews';
import ThreatArsenalCustomViewControls from '../../../../admin/components/threat_arsenal/ThreatArsenalCustomViewControls';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../../store', () => ({ useHelper: () => ({ user_id: 'me' }) }));

const view: CustomView = {
  id: 'view-1',
  label: 'Approved',
  filters: {
    filterGroup: {
      mode: 'and',
      filters: [{
        id: 'approval',
        key: 'action_payload_approval_status',
        mode: 'or',
        operator: 'eq',
        values: ['APPROVED'],
      }],
    },
    textSearch: '',
  },
};

const renderControls = (props: Partial<Parameters<typeof ThreatArsenalCustomViewControls>[0]>, url = '/admin/threat-arsenal') => render(
  <MemoryRouter initialEntries={[url]}>
    <ThreatArsenalCustomViewControls current={view.filters} onDiscard={vi.fn()} {...props} />
  </MemoryRouter>,
);

describe('ThreatArsenalCustomViewControls', () => {
  afterEach(() => {
    cleanup();
    window.localStorage.clear();
  });

  it('given the Active tab with changed filters should say nothing: Active is never saved', () => {
    // Arrange / Act
    renderControls({ current: { textSearch: 'changed' } });

    // Assert
    expect(screen.queryByText('You changed the filters of {name}')).toBeNull();
  });

  it('given a view with its saved filters should say nothing', () => {
    // Arrange / Act
    renderControls({ view });

    // Assert
    expect(screen.queryByText('You changed the filters of {name}')).toBeNull();
  });

  it('given a view whose filters changed should offer to discard them', () => {
    // Arrange
    const onDiscard = vi.fn();
    renderControls({
      view,
      current: { textSearch: 'changed' },
      onDiscard,
    });

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Discard' }));

    // Assert
    expect(screen.getByText('You changed the filters of {name}')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Save as new view' })).toBeTruthy();
    expect(onDiscard).toHaveBeenCalled();
  });

  it('given "New custom view" from the tab bar should open the naming dialog', () => {
    // Arrange / Act
    renderControls({}, '/admin/threat-arsenal?new-view=1');

    // Assert
    expect(screen.getByText('New custom view')).toBeTruthy();
  });
});
