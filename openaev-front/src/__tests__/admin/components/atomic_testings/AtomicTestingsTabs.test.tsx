import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AtomicTestingsTabs from '../../../../admin/components/atomic_testings/AtomicTestingsTabs';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ t: (value: string) => value }),
  };
});

const renderTabs = (path: string, canAccessAssessment = true) => render(
  <AbilityContext.Provider value={{ can: () => canAccessAssessment } as unknown as AppAbility}>
    <MemoryRouter initialEntries={[path]}>
      <AtomicTestingsTabs />
    </MemoryRouter>
  </AbilityContext.Provider>,
);

describe('AtomicTestingsTabs', () => {
  afterEach(() => cleanup());

  it('links the atomic testings list and the IOC validations tab', () => {
    renderTabs('/admin/atomic_testings');
    expect(screen.getByRole('tab', { name: 'Atomic testings' }).getAttribute('href')).toBe('/admin/atomic_testings');
    expect(screen.getByRole('tab', { name: 'IOC validations' }).getAttribute('href')).toBe('/admin/atomic_testings/ioc_validations');
  });

  it('marks the tab of the current page', () => {
    renderTabs('/admin/atomic_testings/ioc_validations');
    expect(screen.getByRole('tab', { name: 'IOC validations' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getByRole('tab', { name: 'Atomic testings' }).getAttribute('aria-current')).toBeNull();
  });

  it('hides the IOC validations tab without access to assessments', () => {
    renderTabs('/admin/atomic_testings', false);
    expect(screen.queryByRole('tab', { name: 'IOC validations' })).toBeNull();
  });
});
