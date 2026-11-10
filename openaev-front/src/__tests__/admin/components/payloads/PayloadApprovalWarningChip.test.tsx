import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadApprovalWarningChip from '../../../../admin/components/payloads/PayloadApprovalWarningChip';
import { type PayloadSimple } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

const renderChip = (
  approvalStatus?: PayloadSimple['payload_approval_status'],
  variant: 'chip' | 'icon' = 'chip',
) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <PayloadApprovalWarningChip approvalStatus={approvalStatus} payloadName="TEST-A" variant={variant} />
    </TooltipProvider>
  </ThemeProvider>,
);

describe('PayloadApprovalWarningChip', () => {
  afterEach(() => {
    cleanup();
  });

  it('shows a "Pending approval" chip, named with the action for assistive technologies', () => {
    // Arrange / Act
    renderChip('PENDING');

    // Assert: same wording as the Threat Arsenal chip; the name carries the action
    expect(screen.getByText('Pending approval')).toBeTruthy();
    expect(screen.getByLabelText('Pending approval – TEST-A')).toBeTruthy();
  });

  it('shows a compact "Rejected" chip for a rejected payload', () => {
    // Arrange / Act
    renderChip('REJECTED');

    // Assert
    expect(screen.getByText('Rejected')).toBeTruthy();
    expect(screen.getByLabelText('Rejected – TEST-A')).toBeTruthy();
  });

  it('can render as an icon only, to sit next to another status', () => {
    // Arrange / Act
    renderChip('PENDING', 'icon');

    // Assert
    expect(screen.getByRole('img', { name: 'Pending approval – TEST-A' })).toBeTruthy();
    expect(screen.queryByText('Pending approval')).toBeNull();
  });

  it('shows a "Superseded" chip for a version replaced before any decision', () => {
    // Arrange / Act
    renderChip('SUPERSEDED');

    // Assert
    expect(screen.getByText('Superseded')).toBeTruthy();
    expect(screen.getByLabelText('Payload version superseded – TEST-A')).toBeTruthy();
  });

  it('renders nothing for an approved payload or an inject without payload', () => {
    // Arrange / Act
    const { container } = renderChip('APPROVED');
    expect(container.textContent).toBe('');
    cleanup();
    const { container: noPayload } = renderChip(undefined);

    // Assert
    expect(noPayload.textContent).toBe('');
  });
});
