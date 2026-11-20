import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import MarketplaceBrowser from '../../../../../components/common/marketplace/page/MarketplaceBrowser';

// -- HELPERS --

const label = (text: string) => <div>{text}</div>;

const renderBrowser = (props: Partial<Parameters<typeof MarketplaceBrowser>[0]> = {}) => render(
  <MarketplaceBrowser
    hero={label('hero')}
    tabs={label('tabs')}
    sidebar={label('sidebar')}
    toolbar={label('toolbar')}
    {...props}
  >
    {label('results')}
  </MarketplaceBrowser>,
);

// The root is the page column, and the box holding the results is the column next to the facets.
const resultsBox = () => screen.getByText('results').parentElement as HTMLElement;
const pageBox = (container: HTMLElement) => container.firstElementChild as HTMLElement;

// -- TESTS --

describe('MarketplaceBrowser', () => {
  afterEach(cleanup);

  it('should render the hero, the tabs, the facets, the toolbar and the results', () => {
    // Arrange + Act
    renderBrowser();

    // Assert
    ['hero', 'tabs', 'sidebar', 'toolbar', 'results'].forEach(name => expect(screen.getByText(name)).toBeInTheDocument());
  });

  it('should let the page set the gap between its hero and its body', () => {
    // Arrange + Act
    const { container } = renderBrowser({ mainBoxStyle: { gap: '8px' } });

    // Assert
    expect(pageBox(container)).toHaveStyle({ gap: '8px' });
  });

  it('should let the page set the gap inside the results column', () => {
    // Arrange + Act
    renderBrowser({ childrenBoxStyle: { gap: '0px' } });

    // Assert
    expect(resultsBox()).toHaveStyle({ gap: '0px' });
  });

  it('should keep the default gaps when the page sets none', () => {
    // Arrange + Act
    const { container } = renderBrowser();

    // Assert: 24px between hero and body, 16px in the results column (theme spacing 3 and 2)
    expect(pageBox(container)).toHaveStyle({ gap: '24px' });
    expect(resultsBox()).toHaveStyle({ gap: '16px' });
  });
});
