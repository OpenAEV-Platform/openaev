import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));

const { default: ScenarioTargetsDrawer } = await import('../../../../../admin/components/scenarios/targets/ScenarioTargetsDrawer');

const renderDrawer = (blocked: boolean, onSubmit = vi.fn().mockResolvedValue(undefined)) => render(
  <ScenarioTargetsDrawer
    overline="Launch"
    title="Phishing"
    version="1.2"
    submitLabel="Launch now"
    launches
    blocked={blocked}
    onSubmit={onSubmit}
    onClose={vi.fn()}
  >
    <div data-testid="targets" />
  </ScenarioTargetsDrawer>,
);

describe('ScenarioTargetsDrawer', () => {
  afterEach(() => {
    cleanup();
  });

  it('given_nothingBlocks_should_showTheFieldsAndSubmit', () => {
    // Arrange
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderDrawer(false, onSubmit);

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Launch now' }));

    // Assert
    expect(screen.getByTestId('targets')).toBeTruthy();
    expect(screen.getByText('v1.2')).toBeTruthy();
    expect(onSubmit).toHaveBeenCalled();
  });

  it('given_somethingBlocks_should_disableTheSubmit', () => {
    // Act
    renderDrawer(true);

    // Assert
    expect((screen.getByRole('button', { name: 'Launch now' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('given_aRefusedSubmit_should_sayWhyInTheDrawer', async () => {
    // Arrange
    renderDrawer(false, vi.fn().mockRejectedValue({ message: 'The scenario runs a payload its release does not name' }));

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Launch now' }));

    // Assert
    expect(await screen.findByText('The scenario runs a payload its release does not name')).toBeTruthy();
    expect(screen.getByTestId('targets')).toBeTruthy();
  });
});
