import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ComponentProps } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../store', () => ({ useHelper: (selector: (helper: { getPlatformSettings: () => unknown }) => unknown) => selector({ getPlatformSettings: () => ({ default_mailer: 'noreply@openaev.io' }) }) }));
vi.mock('../../../../admin/components/common/form/useAskAiAvailability', () => ({
  default: () => ({
    hidden: true,
    isAvailable: false,
  }),
}));
vi.mock('../../../../components/fields/TagField', () => ({ default: ({ label }: { label: string }) => <span>{label}</span> }));
vi.mock('../../../../admin/components/common/filters/DefaultKillChainSelectField', () => ({ default: ({ disabled }: { disabled?: boolean }) => <select aria-label="Default kill chain" disabled={disabled} /> }));

const { default: ScenarioForm } = await import('../../../../admin/components/scenarios/ScenarioForm');

const VALUES = {
  scenario_name: 'Phishing',
  scenario_description: 'A phishing campaign',
  scenario_category: 'attack-scenario',
  scenario_tags: [],
};

const renderForm = (props: ComponentProps<typeof ScenarioForm>) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <ScenarioForm {...props} />
    </TooltipProvider>
  </ThemeProvider>,
);

describe('ScenarioForm', () => {
  afterEach(() => {
    cleanup();
  });

  describe('read only', () => {
    it('given_readOnly_should_showTheValuesInDisabledFields', () => {
      // Act
      const { container } = renderForm({
        readOnly: true,
        initialValues: VALUES,
      });

      // Assert
      expect(screen.getByDisplayValue('Phishing')).toBeTruthy();
      expect(screen.getByDisplayValue('A phishing campaign')).toBeTruthy();
      const fields = [...container.querySelectorAll('input:not([aria-hidden]):not([type=hidden]), textarea'), ...screen.getAllByRole('combobox')];
      expect(fields.length).toBeGreaterThan(0);
      fields.forEach(field => expect((field as HTMLInputElement).disabled).toBe(true));
    });

    it('given_readOnly_should_leaveOutTagsModulesEmailsAndButtons', () => {
      // Act
      renderForm({
        readOnly: true,
        initialValues: VALUES,
      });

      // Assert
      expect(screen.queryByText('Tags')).toBeNull();
      expect(screen.queryByText('Modules')).toBeNull();
      expect(screen.queryByText('Emails and SMS')).toBeNull();
      expect(screen.queryAllByRole('button', { name: /Cancel|Create|Update/ })).toHaveLength(0);
    });
  });

  describe('editable', () => {
    it('given_aScenarioToCreate_should_showEverySectionAndItsButtons', () => {
      // Act
      renderForm({
        initialValues: VALUES,
        onSubmit: vi.fn(),
        handleClose: vi.fn(),
      });

      // Assert
      expect(screen.getByText('Tags')).toBeTruthy();
      expect(screen.getByText('Emails and SMS')).toBeTruthy();
      expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy();
      expect(screen.getByRole('button', { name: 'Create' })).toBeTruthy();
      expect((screen.getByDisplayValue('Phishing') as HTMLInputElement).disabled).toBe(false);
    });
  });
});
