import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ChangeDiff from '../../../../components/common/update/ChangeDiff';

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  return {
    ...actual,
    useFormatter: () => ({ t: (message: string, values?: Record<string, string>) => message.replace(/\{(\w+)\}/g, (_, key: string) => values?.[key] ?? key) }),
  };
});

describe('ChangeDiff', () => {
  afterEach(cleanup);

  it('shows each field as removed and added lines, the changed words marked', () => {
    // Act
    render(
      <ChangeDiff
        rows={[{
          id: 'variables/target',
          field: 'Variables · Variable target',
          before: 'echo start\n10.0.0.1',
          after: 'echo start\n10.0.0.2',
        }]}
        beforeLabel="Before · now"
        afterLabel="After · v1.1.0"
      />,
    );

    // Assert
    const changes = screen.getByRole('group', { name: 'Changes' });
    expect(within(changes).getByText('Before · now')).toBeInTheDocument();
    expect(within(changes).getByText('After · v1.1.0')).toBeInTheDocument();
    const field = within(changes).getByRole('group', { name: 'Variables · Variable target · 1 lines added, 1 lines removed' });
    expect(within(field).getByText('+1')).toBeInTheDocument();
    expect(within(field).getByText('−1')).toBeInTheDocument();
    expect(field.querySelectorAll('[data-kind="same"]')).toHaveLength(1);
    expect(field.querySelector('[data-kind="removed"] mark')).toHaveTextContent('10.0.0.1');
    expect(field.querySelector('[data-kind="added"] mark')).toHaveTextContent('10.0.0.2');
  });
});
