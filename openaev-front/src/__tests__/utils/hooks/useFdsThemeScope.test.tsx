import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { FDS } from '../../../components/fds-tokens.generated';
import useFdsThemeScope, { type FdsCustomTheme, type FdsThemeMode } from '../../../utils/hooks/useFdsThemeScope';

afterEach(() => {
  cleanup();
  document.documentElement.removeAttribute('style');
  document.documentElement.className = '';
});

const Harness = ({ mode, custom }: {
  mode: FdsThemeMode;
  custom: FdsCustomTheme;
}) => {
  useFdsThemeScope(mode, custom);
  return null;
};

const read = (token: string) => document.documentElement.style.getPropertyValue(token);

// One setting, one token. The bridge is the only path a custom colour has to the
// library, so a field that stops landing here stops being customisable at all.
const DIRECT = [
  ['background', '--bg-elevation-default-layer-0'],
  ['paper', '--bg-elevation-default-layer-1'],
  ['accent', '--bg-elevation-default-layer-3'],
  ['nav', '--bg-elevation-heading-layer-0'],
  ['primary', '--color-filigran-brand-primary'],
  ['secondary', '--color-filigran-tonic-primary'],
  ['text', '--text-default-primary'],
] as const;

describe('useFdsThemeScope', () => {
  it.each(DIRECT)('lands %s on its token', (setting, token) => {
    render(<Harness mode="dark" custom={{ [setting]: '#ff00aa' }} />);
    expect(read(token)).toBe('#ff00aa');
  });

  it('writes nothing when every field is unset', () => {
    render(<Harness mode="dark" custom={{}} />);
    expect(document.documentElement.getAttribute('style')).toBeFalsy();
  });

  it('removes what it wrote when a field is cleared', () => {
    const { rerender } = render(<Harness mode="dark" custom={{ paper: '#7a1fa2' }} />);
    expect(read('--bg-elevation-default-layer-1')).toBe('#7a1fa2');
    rerender(<Harness mode="dark" custom={{}} />);
    expect(read('--bg-elevation-default-layer-1')).toBe('');
    expect(document.documentElement.getAttribute('style')).toBeFalsy();
  });

  it('ignores a value equal to the library default, so the theme stays free to move', () => {
    render(<Harness mode="dark" custom={{ paper: FDS.colors.dark['--bg-elevation-default-layer-1'] }} />);
    expect(read('--bg-elevation-default-layer-1')).toBe('');
  });

  it('derives the drawer layer from paper', () => {
    render(<Harness mode="dark" custom={{ paper: '#7a1fa2' }} />);
    expect(read('--bg-elevation-default-layer-2')).toMatch(/^#[\da-f]{6}$/i);
    expect(read('--bg-elevation-default-layer-2')).not.toBe('#7a1fa2');
  });

  it('moves the brand hover with the brand, so a state does not change colour scheme', () => {
    render(<Harness mode="dark" custom={{ primary: '#ff00aa' }} />);
    expect(read('--color-filigran-brand-tertiary')).toMatch(/^#[\da-f]{6}$/i);
    expect(read('--color-filigran-brand-tertiary')).not.toBe(FDS.colors.dark['--color-filigran-brand-tertiary']);
  });

  it('carries a customised surface to the rest of its own layer', () => {
    render(<Harness mode="dark" custom={{ paper: '#7a1fa2' }} />);
    expect(read('--bg-elevation-highlight-layer-1')).toMatch(/^#[\da-f]{6}$/i);
    expect(read('--border-elevation-subtle-soft-layer-1')).toMatch(/^#[\da-f]{6}$/i);
  });

  it('owns the mode class on the document root', () => {
    const { rerender } = render(<Harness mode="dark" custom={{}} />);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    rerender(<Harness mode="light" custom={{}} />);
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });
});
