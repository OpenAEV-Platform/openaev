import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import PlatformIcon from '../../components/PlatformIcon';

describe('PlatformIcon', () => {
  afterEach(cleanup);

  // jsdom does no layout, so the guard is on the declared style. WebKit resolves a
  // `fit-content` height to the bitmap's own 800px, which turned a 16px logo into a
  // 16x800 sliver in Safari; an `auto` height would let a flex row stretch it.
  it.each(['Windows', 'Linux', 'MacOS'])('given_%s_should_renderASquareLogo', (platform) => {
    // Arrange / Act
    render(<PlatformIcon platform={platform} width={16} />);

    // Assert
    const logo = screen.getByRole('img', { name: platform });
    expect(logo.style.width).toBe('16px');
    expect(logo.style.height).toBe('16px');
  });
});
