import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AtomicTestingsTabs from '../../../../admin/components/atomic_testings/AtomicTestingsTabs';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../utils/permissions/permissionsContext';

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ t: (value: string) => value }),
  };
});

const renderTabs = (path: string, canAccessAssessment = true) => render(
  <AbilityProvider value={{
    can: () => canAccessAssessment,
    on: () => () => {},
  } as unknown as AppAbility}
  >
    <MemoryRouter initialEntries={[path]}>
      <AtomicTestingsTabs />
    </MemoryRouter>
  </AbilityProvider>,
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

  it('keeps the IOC validations tab current on an IOC validation detail page', () => {
    renderTabs('/admin/atomic_testings/ioc_validations/2c9a7d4e-0000-4000-8000-000000000001');
    expect(screen.getByRole('tab', { name: 'IOC validations' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getByRole('tab', { name: 'Atomic testings' }).getAttribute('aria-current')).toBeNull();
  });

  it('hides the IOC validations tab without access to assessments', () => {
    renderTabs('/admin/atomic_testings', false);
    expect(screen.queryByRole('tab', { name: 'IOC validations' })).toBeNull();
  });
});
