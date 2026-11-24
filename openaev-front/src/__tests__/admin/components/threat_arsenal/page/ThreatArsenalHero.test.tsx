import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock(import('../../../../../utils/utils'), async importOriginal => ({
  ...await importOriginal(),
  isFeatureEnabled: () => true,
}));
vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ searchThreatArsenalActions: () => Promise.resolve({ data: { totalElements: 12 } }) }));
vi.mock('../../../../../actions/catalog/payload-catalog-actions', () => ({ searchAvailableActions: () => Promise.resolve({ data: { totalElements: 4 } }) }));

const { default: ThreatArsenalHero } = await import('../../../../../admin/components/threat_arsenal/page/ThreatArsenalHero');

describe('ThreatArsenalHero', () => {
  afterEach(() => {
    cleanup();
  });

  it('given_theCatalogue_should_showTheActiveAndNotInstalledCounts', async () => {
    // Act
    render(
      <TooltipProvider>
        <MemoryRouter>
          <ThreatArsenalHero />
        </MemoryRouter>
      </TooltipProvider>,
    );

    // Assert
    await waitFor(() => expect(screen.getByRole('link', { name: /Not installed/ }).textContent).toContain('4'));
    expect(screen.getByRole('link', { name: /Not installed/ }).getAttribute('href')).toBe('/admin/threat-arsenal/available');
    expect(screen.getByRole('link', { name: /Active/ }).textContent).toContain('12');
  });
});
