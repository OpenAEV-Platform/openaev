import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadApprovalWarningChip from '../../../../admin/components/payloads/PayloadApprovalWarningChip';
import { type PayloadSimple } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

const renderChip = (approvalStatus?: PayloadSimple['payload_approval_status']) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <PayloadApprovalWarningChip approvalStatus={approvalStatus} />
    </TooltipProvider>
  </ThemeProvider>,
);

describe('PayloadApprovalWarningChip', () => {
  afterEach(() => {
    cleanup();
  });

  it('shows "Payload pending approval" for a pending payload', () => {
    renderChip('PENDING');
    expect(screen.queryByText('Payload pending approval')).not.toBeNull();
  });

  it('shows "Payload rejected" for a rejected payload', () => {
    renderChip('REJECTED');
    expect(screen.queryByText('Payload rejected')).not.toBeNull();
  });

  it('renders nothing for an approved payload or an inject without payload', () => {
    const { container } = renderChip('APPROVED');
    expect(container.textContent).toBe('');
    cleanup();
    const { container: noPayload } = renderChip(undefined);
    expect(noPayload.textContent).toBe('');
  });
});
