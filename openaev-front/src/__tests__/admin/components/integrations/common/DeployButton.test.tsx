import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string, values?: Record<string, number>) => value.replace('{count}', String(values?.count ?? '')) }) }));
const enterpriseEdition = { isValidated: true };
vi.mock('../../../../../utils/hooks/useEnterpriseEdition', () => ({
  default: () => ({
    isValidated: enterpriseEdition.isValidated,
    openDialog: vi.fn(),
    setEEFeatureDetectedInfo: vi.fn(),
  }),
}));
vi.mock('../../../../../admin/components/common/entreprise_edition/EEChip', () => ({ default: () => <span data-testid="ee-chip" /> }));

import DeployButton, { DeployedChip } from '../../../../../admin/components/integrations/common/DeployButton';

const renderWithProvider = (node: ReactNode) => render(<TooltipProvider>{node}</TooltipProvider>);

describe('DeployButton', () => {
  afterEach(() => {
    cleanup();
    enterpriseEdition.isValidated = true;
  });

  describe('DeployedChip', () => {
    it('given_oneInstance_should_sayDeployed', () => {
      // Arrange / Act
      renderWithProvider(<DeployedChip count={1} />);

      // Assert
      expect(screen.getByText('Deployed')).toBeInTheDocument();
    });

    it('given_severalInstances_should_sayHowMany', () => {
      // Arrange / Act
      renderWithProvider(<DeployedChip count={2} />);

      // Assert
      expect(screen.getByText('2 deployed')).toBeInTheDocument();
    });
  });

  it('given_inCard_should_renderOnlyTheButtonWithoutIcon', () => {
    // Arrange / Act
    renderWithProvider(<DeployButton onDeployBtnClick={vi.fn()} deploymentCount={2} size="sm" inCard />);

    // Assert
    const button = screen.getByRole('button', { name: 'Deploy' });
    expect(button.querySelector('svg')).toBeNull();
    expect(screen.queryByText('2 deployed')).not.toBeInTheDocument();
  });

  it('given_inCardWithoutEnterpriseEdition_should_matchTheConnectorPageButton', () => {
    // Arrange
    enterpriseEdition.isValidated = false;
    const onDeployBtnClick = vi.fn();

    // Act
    renderWithProvider(<DeployButton onDeployBtnClick={onDeployBtnClick} deploymentCount={0} size="sm" inCard />);

    // Assert
    const button = screen.getByRole('button', { name: /Deploy/ });
    expect(button).toBeEnabled();
    expect(button).toContainElement(screen.getByTestId('ee-chip'));
    fireEvent.click(button);
    expect(onDeployBtnClick).not.toHaveBeenCalled();
  });

  it('given_inCardWithEnterpriseEdition_should_enableTheButtonWithoutEEChip', () => {
    // Arrange / Act
    renderWithProvider(<DeployButton onDeployBtnClick={vi.fn()} deploymentCount={0} size="sm" inCard />);

    // Assert
    expect(screen.getByRole('button', { name: 'Deploy' })).toBeEnabled();
    expect(screen.queryByTestId('ee-chip')).not.toBeInTheDocument();
  });

  it('given_outsideACard_should_keepTheDeployedChip', () => {
    // Arrange / Act
    renderWithProvider(<DeployButton onDeployBtnClick={vi.fn()} deploymentCount={2} />);

    // Assert
    expect(screen.getByText('2 deployed')).toBeInTheDocument();
  });
});
