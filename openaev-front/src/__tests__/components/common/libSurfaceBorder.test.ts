import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import LIB_SURFACE_BORDER, { LIB_SURFACE_LAYER } from '../../../components/common/libSurfaceBorder';

/**
 * Reads the INSTALLED design-system dist, not a copy of it.
 *
 * The product paints a MUI surface with a library token so an unconvertible
 * surface still reads as one with its converted neighbours. Nothing else can
 * catch it if the library stops publishing that token: an unresolvable `var()`
 * invalidates the whole `border` shorthand and the border becomes `0px none` —
 * no type error, no lint error, no build error. jsdom applies no stylesheet, so
 * a render assertion would prove nothing either.
 *
 * What this file asserts is that the NAME the product writes is a name the
 * shipped stylesheet actually defines, and defines per layer.
 */
const require = createRequire(import.meta.url);
const distCss = readFileSync(
  path.join(path.dirname(require.resolve('@filigran/design-system')), 'index.css'),
  'utf8',
);

const varName = LIB_SURFACE_BORDER.match(/var\((--[a-z0-9-]+)\)/)?.[1] ?? '';

describe('libSurfaceBorder against the installed design system', () => {
  it('names a custom property, and not a diluted variant whose opacity is in its name', () => {
    expect(varName).not.toBe('');
    // `-transparency-15` is renamed whenever the opacity changes — the library
    // has already gone 40 -> 15. Reading it is what makes the border vanish.
    expect(varName).not.toMatch(/transparency-\d+/);
  });

  it('reads a property the shipped stylesheet defines', () => {
    expect(distCss).toContain(`${varName}:`);
  });

  it('reads the property the library redeclares per layer, and applies that scope', () => {
    // The alias only means "layer 1" inside `.layer-1`. Without the scope it
    // falls back to :root, i.e. layer 0 — the same colour today, by accident.
    const layerBlock = new RegExp(`\\.${LIB_SURFACE_LAYER}\\{[^}]*${varName}:`);
    expect(distCss).toMatch(layerBlock);
  });

  it('reads the same property the library Paper paints its own border with', () => {
    const libJs = readFileSync(
      path.join(path.dirname(require.resolve('@filigran/design-system')), 'index.js'),
      'utf8',
    );
    // The Paper carries the property name, minus the leading dashes, as its
    // border utility class — measured on the rendered surface:
    // `box-border border border-elevation-subtle-soft layer-1`.
    expect(libJs).toContain(varName.replace(/^--/, ''));
  });
});
