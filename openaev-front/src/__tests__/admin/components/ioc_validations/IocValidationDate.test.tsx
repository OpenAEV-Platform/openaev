import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import IocValidationDate from '../../../../admin/components/ioc_validations/IocValidationDate';
import { IOC_VALIDATION_FOCUS_RING_CLASS } from '../../../../admin/components/ioc_validations/iocValidationUtils';

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ fldt: (value: string) => value }),
  };
});

const renderDate = (focusable?: boolean) => render(
  <TooltipProvider>
    <IocValidationDate date="2026-10-05T20:00:00Z" focusable={focusable} />
  </TooltipProvider>,
).container.querySelector('time') as HTMLElement;

describe('IocValidationDate', () => {
  afterEach(() => cleanup());

  it('is a focus stop with the focus ring of the design system by default', () => {
    const date = renderDate();
    expect(date.getAttribute('datetime')).toBe('2026-10-05T20:00:00Z');
    expect(date.getAttribute('tabindex')).toBe('0');
    expect(date.className.split(' ')).toEqual(expect.arrayContaining(IOC_VALIDATION_FOCUS_RING_CLASS.split(' ')));
  });

  it('is no focus stop of its own inside a link', () => {
    const date = renderDate(false);
    expect(date.hasAttribute('tabindex')).toBe(false);
    expect(date.className).toBe('');
  });
});
