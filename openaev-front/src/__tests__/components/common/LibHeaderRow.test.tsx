import { Paper } from '@filigran/design-system';
import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import LibHeaderRow, { LIB_HEADER_GAP, LIB_HEADER_ROW_HEIGHT, LIB_HEADER_TITLE_CLASSES } from '../../../components/common/LibHeaderRow';

/**
 * Divergence guard between the library `Paper` header and the product's
 * alignment of it.
 *
 * `LibHeaderRow` exists because five configuration sections have no surface and
 * must not gain one, so their header is ALIGNED on the library's instead of
 * being converted. That alignment copies a specification the library owns: the
 * day the library moves its header, those five drift silently.
 *
 * This test renders BOTH and fails if either side moves. It asserts classes and
 * structure, never computed style — jsdom does not apply the library
 * stylesheet, so a font-size read here would be meaningless.
 */

const TITLE = 'Panel title';
const ACTION_LABEL = 'Add';
const BODY = 'body';

/** The library's header row is the surface's previous sibling. */
const libHeaderRow = () => {
  const { container } = render(
    <Paper title={TITLE} action={<button type="button">{ACTION_LABEL}</button>}>{BODY}</Paper>,
  );
  const surface = container.querySelector('[class*="bg-elevation-default"]');
  if (!surface?.previousElementSibling) throw new Error('no library header row rendered');
  return surface.previousElementSibling as HTMLElement;
};

describe('LibHeaderRow mirrors the library Paper header', () => {
  afterEach(cleanup);

  it('carries the exact typography classes the library puts on its title', () => {
    const row = libHeaderRow();
    // The library composes the typography on the ROW, as one composite class
    // plus the colour. Both must be what the product copies.
    for (const token of LIB_HEADER_TITLE_CLASSES.split(' ')) {
      expect(row.className).toContain(token);
    }
  });

  it('uses the composite class, never the four split utilities', () => {
    // `text-content-compact font-content-compact leading-* tracking-*` carry no
    // weight: the title would inherit its ancestor's. The library paid for this
    // twice; the product must not repay it.
    const row = libHeaderRow();
    expect(row.className).toContain('content-compact');
    expect(row.className).not.toMatch(/\btext-content-compact\b/);
    expect(row.className).not.toMatch(/\bfont-content-compact\b/);
  });

  it('keeps the row metrics the library declares', () => {
    const row = libHeaderRow();
    // h-6 = 24px, gap-2 = 8px. The product reproduces them as numbers, so a
    // change on either side breaks this pair.
    expect(row.className).toContain('h-6');
    expect(row.className).toContain('gap-2');
    expect(LIB_HEADER_ROW_HEIGHT).toBe(24);
    expect(LIB_HEADER_GAP).toBe(8);
  });

  it('renders the same anatomy product-side: title, then action, in one row', () => {
    const { getByTestId, getByRole } = render(
      <LibHeaderRow title={TITLE} action={<button type="button">{ACTION_LABEL}</button>}>
        <div>{BODY}</div>
      </LibHeaderRow>,
    );
    const row = getByTestId('lib-header-row');
    expect(row.style.height).toBe(`${LIB_HEADER_ROW_HEIGHT}px`);
    expect(row.style.gap).toBe(`${LIB_HEADER_GAP}px`);
    expect(row.style.alignItems).toBe('center');
    expect(row.contains(getByRole('button', { name: ACTION_LABEL }))).toBe(true);
  });

  it('applies the library title classes to the product title', () => {
    const { getByText } = render(
      <LibHeaderRow title={TITLE}><div>{BODY}</div></LibHeaderRow>,
    );
    const titre = getByText(TITLE);
    for (const token of LIB_HEADER_TITLE_CLASSES.split(' ')) {
      expect(titre.className).toContain(token);
    }
  });
});
