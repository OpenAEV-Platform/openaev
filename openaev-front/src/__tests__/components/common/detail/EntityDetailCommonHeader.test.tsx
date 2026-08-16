import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { InformationGrid, Section, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';

/**
 * Render guard for the three titled wrappers, added WITH the conversion rather
 * than after it.
 *
 * Why it exists: the two guards `check-fds-conformity.mjs` runs look at imports
 * and at hardcoded paddings. Neither can see a header. Nothing else in this
 * suite pins these three wrappers either, so a green gate proves nothing about
 * them — a revert to the product's own uppercase header, or a title that stops
 * reaching the library's slot, would pass every check silently.
 *
 * What is asserted here is STRUCTURE, not computed style: jsdom does not apply
 * the library's stylesheet, so a colour or a font-size read here would be
 * meaningless. The pixel values (12px / 400 / no uppercase / 24px row / 8px
 * gap) are measured in a real browser and recorded in
 * fds-migration/PAPER-GAP-INVENTORY.md — this file guards the wiring that makes
 * those values apply at all.
 */

/** The library paints its surface with this class; the product never does. */
const SURFACE = '[class*="bg-elevation-default"]';

const surfaceOf = (container: HTMLElement) => {
  const surface = container.querySelector(SURFACE);
  if (!surface) throw new Error('no library Paper surface rendered');
  return surface as HTMLElement;
};

/** The header the library renders is the surface's PREVIOUS sibling. */
const headerOf = (container: HTMLElement) => {
  const previous = surfaceOf(container).previousElementSibling;
  if (!previous) throw new Error('no header row rendered before the surface');
  return previous as HTMLElement;
};

describe('EntityDetailCommon — titled wrappers use the library header', () => {
  afterEach(cleanup);

  describe.each([
    ['Section', (title: string) => <Section title={title}>body</Section>],
    ['InformationGrid', (title: string) => <InformationGrid title={title}>body</InformationGrid>],
    ['SectionBlock', (title: string) => <SectionBlock title={title}>body</SectionBlock>],
  ])('%s', (_name, renderWrapper) => {
    it('renders the title in the library header, outside the surface', () => {
      const { container } = render(renderWrapper('Panel title'));
      const header = headerOf(container);
      expect(header.textContent).toContain('Panel title');
      // The title must NOT be inside the surface: the library puts its header
      // above it, outside its border and padding.
      expect(surfaceOf(container).textContent).not.toContain('Panel title');
    });

    it('does not force the title to uppercase product-side', () => {
      // The product's own SECTION_LABEL_SX carried `textTransform: uppercase`.
      // Adopting the library slot means the casing is the library's business,
      // and nothing here may re-impose it through an inline style.
      const { container } = render(renderWrapper('Panel title'));
      const header = headerOf(container);
      const styled = [header, ...Array.from(header.querySelectorAll('*'))] as HTMLElement[];
      for (const node of styled) {
        expect(node.style.textTransform).toBe('');
        expect(node.style.fontFamily).toBe('');
      }
    });

    it('keeps flex on the surface and stretches through a grid row', () => {
      // The library's wrapper cannot be styled by a consumer: `style` reaches
      // the SURFACE. The wrapper is stretched by the product's own container
      // being a one-row grid — measured 58px vs 130px without it, see
      // PAPER-GAP-INVENTORY §13.2.
      const { container } = render(renderWrapper('Panel title'));
      // jsdom normalises the shorthand: `flex: 1` reads back as `1 1 0%`.
      expect(surfaceOf(container).style.flex).toBe('1 1 0%');
      const outer = container.firstElementChild as HTMLElement;
      expect(outer.style.display).toBe('grid');
      expect(outer.style.gridTemplateRows).toBe('1fr');
    });
  });

  it('renders the action in the header, not in the surface', () => {
    const { container } = render(
      <SectionBlock title="With action" action={<button type="button">Add</button>}>body</SectionBlock>,
    );
    expect(headerOf(container).contains(screen.getByRole('button', { name: 'Add' }))).toBe(true);
    expect(surfaceOf(container).contains(screen.getByRole('button', { name: 'Add' }))).toBe(false);
  });

  it('still renders a header row for an empty title — measured, not assumed', () => {
    // The library keys its header on the PRESENCE of the prop, not on its
    // truthiness: `title=""` renders an empty 24px row rather than no row.
    // Pinned here because these three wrappers always pass `title`, so the
    // header is unconditional for them — a call site whose title resolves to
    // an empty string would show a blank band, not a tight panel.
    // No such call site exists today: all `?? ''` titles in the product belong
    // to Drawer/Tooltip, never to these wrappers. This test is the tripwire if
    // that changes.
    const { container } = render(<SectionBlock title="">body</SectionBlock>);
    const header = surfaceOf(container).previousElementSibling as HTMLElement;
    expect(header).not.toBeNull();
    expect(header.textContent).toBe('');
  });
});
