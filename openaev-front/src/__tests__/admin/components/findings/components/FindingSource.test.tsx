import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FindingSource from '../../../../../admin/components/findings/components/FindingSource';
import { type InjectorSimple } from '../../../../../utils/api-types';

// The icon fetches the injector logo from the API: stub it, the sources cell only decides how many to show.
vi.mock('../../../../../admin/components/common/injectors/InjectorIcon', () => ({ default: ({ type }: { type: string }) => <img alt={type} /> }));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

const buildSources = (count: number): InjectorSimple[] => Array.from({ length: count }, (_, index) => ({
  injector_id: `injector-${index}`,
  injector_name: `Injector ${index}`,
  injector_type: `type_${index}`,
}));

const renderSources = (element: ReactElement) => render(<TooltipProvider>{element}</TooltipProvider>);

describe('FindingSource', () => {
  afterEach(cleanup);

  describe('when there is no source', () => {
    it.each([
      ['undefined', undefined],
      ['an empty list', []],
    ])('shows a dash for %s', (_label, sources) => {
      // Arrange & Act
      renderSources(<FindingSource sources={sources} />);

      // Assert
      expect(screen.getByText('-')).toBeTruthy();
      expect(screen.queryAllByRole('img')).toHaveLength(0);
    });
  });

  describe('when there are sources', () => {
    it('shows one icon per source, without any counter, up to three sources', () => {
      // Arrange & Act
      renderSources(<FindingSource sources={buildSources(3)} />);

      // Assert
      expect(screen.getAllByRole('img').map(icon => icon.getAttribute('alt'))).toEqual(['type_0', 'type_1', 'type_2']);
      expect(screen.queryByText(/^\+/)).toBeNull();
      expect(screen.queryByText('-')).toBeNull();
    });

    it('shows the first three icons and counts the others in a +N chip', () => {
      // Arrange & Act
      renderSources(<FindingSource sources={buildSources(5)} />);

      // Assert
      expect(screen.getAllByRole('img').map(icon => icon.getAttribute('alt'))).toEqual(['type_0', 'type_1', 'type_2']);
      expect(screen.getByText('+2')).toBeTruthy();
    });
  });
});
