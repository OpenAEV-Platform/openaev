import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ComponentProps } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type Domain, type ThreatArsenalAction } from '../../../../utils/api-types';

const domains: Domain[] = [
  {
    domain_id: 'endpoint',
    domain_name: 'Endpoint',
    domain_color: '#389CFF',
  },
  {
    domain_id: 'network',
    domain_name: 'Network',
    domain_color: '#009933',
  },
  {
    domain_id: 'unclassified',
    domain_name: 'To classify',
    domain_color: '#FFFFFF',
  },
];

vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    tPick: (labels?: Record<string, string>) => labels?.en ?? '',
    nsdt: (value: string) => value,
  }),
}));

vi.mock('../../../../store', () => ({ useHelper: (selector: (helper: { getDomains: () => Domain[] }) => unknown) => selector({ getDomains: () => domains }) }));

// Children that read the store on their own; the card only places them.
vi.mock('../../../../components/ItemTags', () => ({ default: () => null }));
vi.mock('../../../../admin/components/common/injects/InjectIcon', () => ({ default: () => <span /> }));
vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalActionPopover', () => ({ default: () => <button type="button" aria-label="More actions" /> }));

import ThreatArsenalCard from '../../../../admin/components/threat_arsenal/ThreatArsenalCard';

const theme = createTheme();

const buildAction = (overrides: Partial<ThreatArsenalAction> = {}): ThreatArsenalAction => ({
  injector_contract_id: 'contract-1',
  injector_contract_updated_at: '2026-10-16T10:00:00Z',
  action_labels: { en: 'Dump credentials' },
  action_attack_patterns_ids: [],
  action_domains_ids: ['endpoint', 'network', 'unclassified'],
  action_platforms: ['Windows'],
  action_payload: {
    payload_id: 'payload-1',
    payload_type: 'Command',
    payload_status: 'VERIFIED',
  },
  ...overrides,
});

const renderCard = (props: Partial<ComponentProps<typeof ThreatArsenalCard>> = {}) => {
  const handlers = {
    onSelect: vi.fn(),
    onToggleEntity: vi.fn(),
  };
  render(
    <ThemeProvider theme={theme}>
      <TooltipProvider>
        <ThreatArsenalCard
          action={buildAction()}
          selected={false}
          checked={false}
          anySelected={false}
          onUpdate={vi.fn()}
          onDuplicate={vi.fn()}
          onDelete={vi.fn()}
          disableUpdate={false}
          disableDuplicate={false}
          disableJsonExport={false}
          disableDelete={false}
          {...handlers}
          {...props}
        />
      </TooltipProvider>
    </ThemeProvider>,
  );
  return handlers;
};

describe('ThreatArsenalCard', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  describe('content', () => {
    it('given_aPayload_should_showItsTypeStatusAndDomains', () => {
      // Arrange / Act
      renderCard();

      // Assert
      expect(screen.getByText('Command')).toBeInTheDocument();
      expect(screen.getByText('Dump credentials')).toBeInTheDocument();
      expect(screen.getByText('Verified')).toBeInTheDocument();
      expect(screen.getByText('Endpoint')).toBeInTheDocument();
      // "To classify" is not a domain to show: only Network is behind the +1.
      expect(screen.getByText('+1')).toBeInTheDocument();
      expect(screen.queryByText('To classify')).not.toBeInTheDocument();
    });

    it('given_noPayload_should_showTheInjectorOverlineWithoutStatus', () => {
      // Arrange / Act
      renderCard({ action: buildAction({ action_payload: undefined }) });

      // Assert
      expect(screen.getByText('Injector')).toBeInTheDocument();
      expect(screen.queryByText('Verified')).not.toBeInTheDocument();
    });

    it('given_moreThanFourPlatforms_should_collapseTheRest', () => {
      // Arrange / Act
      renderCard({ action: buildAction({ action_platforms: ['Windows', 'Linux', 'MacOS', 'Android', 'iOS', 'Container'] }) });

      // Assert
      expect(screen.getByText('+2')).toBeInTheDocument();
    });
  });

  describe('interactions', () => {
    it('given_aClickOnTheCard_should_openTheAction', () => {
      // Arrange
      const { onSelect } = renderCard();

      // Act
      fireEvent.click(screen.getByText('Dump credentials'));

      // Assert
      expect(onSelect).toHaveBeenCalledTimes(1);
    });

    it('given_aClickOnTheCheckbox_should_toggleTheSelectionOnly', () => {
      // Arrange
      const { onSelect, onToggleEntity } = renderCard();

      // Act
      fireEvent.click(screen.getByRole('checkbox', { name: 'Dump credentials' }));

      // Assert
      expect(onToggleEntity).toHaveBeenCalledTimes(1);
      expect(onSelect).not.toHaveBeenCalled();
    });
  });
});
