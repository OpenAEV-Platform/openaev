import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BeforeAfterTable from '../../../../components/common/update/BeforeAfterTable';

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  return {
    ...actual,
    useFormatter: () => ({ t: (message: string) => message }),
  };
});

describe('BeforeAfterTable', () => {
  afterEach(cleanup);

  it('shows each field before and after, the changed words marked', () => {
    // Act
    render(
      <BeforeAfterTable
        rows={[{
          id: 'inject:a/title',
          field: 'Scan · Title',
          before: 'nmap -A',
          after: 'nmap -sV',
        }]}
        beforeLabel="Before · now"
        afterLabel="After · v1.1.0"
      />,
    );

    // Assert
    const table = screen.getByRole('table', { name: 'Changes' });
    expect(within(table).getByRole('columnheader', { name: 'After · v1.1.0' })).toBeInTheDocument();
    const row = within(table).getByText('Scan · Title').closest('tr') as HTMLElement;
    expect(row.querySelectorAll('mark')).toHaveLength(2);
    expect(within(row).getByText('-A')).toBeInTheDocument();
    expect(within(row).getByText('-sV')).toBeInTheDocument();
  });
});
