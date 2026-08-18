import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NAV_OPEN_STORAGE_KEY } from '../../../../components/common/menu/navbar/useNavbarState';
import { expectLibraryIconButton, expectLibrarySearchField, expectNoMuiControls } from '../../../utils/designSystemAssertions';

// The bar's own dependencies are not what this file is about: it covers the
// contract between the product and the design system `Header`. Everything that
// would drag in Relay, the redux store or the chatbot context is stubbed down
// to a marker, so a failure here can only mean the Header wiring broke.
vi.mock('../../../../admin/components/nav/TopBarNotifications', () => ({ default: () => null }));
vi.mock('../../../../admin/components/nav/BulkOperationsIndicator', () => ({ default: () => null }));
vi.mock('../../../../admin/components/ariane/AskArianeButton', () => ({ default: () => null }));
vi.mock('../../../../admin/components/ariane/CtemCommandCenterButton', () => ({ default: () => null }));
vi.mock('../../../../admin/components/ariane/AskArianePanel', () => ({ default: () => null }));
vi.mock('../../../../admin/components/ariane/useChatbotHooks', () => ({ useChatbot: () => ({ open: false }) }));
vi.mock('../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
// Mutable so a test can put the platform on a non-production licence, which is
// the only state that renders the "EE DEV LICENSE" tag.
const platformLicense: { license_type?: string } = {};
vi.mock('../../../../utils/hooks/useAuth', () => ({ default: () => ({ settings: { platform_license: platformLicense } }) }));

const { default: TopBar, OPEN_BAR_WIDTH, SMALL_BAR_WIDTH } = await import('../../../../admin/components/nav/TopBar');

// The two colours a customer can drive through the platform's background_color
// setting, as palette.background.gradient carries them to the bar.
const GRADIENT_START = 'rgb(1, 2, 3)';
const GRADIENT_END = 'rgb(4, 5, 6)';

const renderTopBar = () => {
  const theme = createTheme({
    palette: {
      background: {
        gradient: {
          start: GRADIENT_START,
          end: GRADIENT_END,
        },
      },
    } as never,
  });
  return render(
    <MemoryRouter>
      <IntlProvider locale="en" messages={{}}>
        <ThemeProvider theme={theme}>
          <TopBar />
        </ThemeProvider>
      </IntlProvider>
    </MemoryRouter>,
  );
};

// The Header renders a <header>, which is the `banner` landmark only while it
// is not nested in another landmark - it is not here.
const getBar = () => screen.getByRole('banner');

describe('Admin TopBar built on the design system Header', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => {
    cleanup();
    localStorage.clear();
    delete platformLicense.license_type;
  });

  it('tags a non-production licence with the design system Chip, not a MUI one', () => {
    // Review #7305: the "EE DEV LICENSE" tag was the last MUI control
    // left in the bar - a MUI `Chip` inside the product's generic `ItemBoolean`,
    // painted from hardcoded literals (#f44336 on rgba(244, 67, 54, 0.08)).
    // Same treatment as the EE chip the Navbar pilot already converted
    // (LeftBarTenantSwitcher): the library's Chip, coloured by a token.
    // `severity="critical"` is the faithful mapping - it resolves to
    // `bg-feedback-error-secondary-transparency` + `text-feedback-error-primary`,
    // which is the same anatomy the literals produced.
    platformLicense.license_type = 'nfr';
    renderTopBar();
    const tag = screen.getByText('EE DEV LICENSE');
    // Negative: no MUI control anywhere in the bar, the tag included.
    expectNoMuiControls(getBar(), 'the bar with a dev licence');
    // Positive: the colour comes from a feedback TOKEN, not from a literal.
    const painted = [tag, tag.parentElement, tag.parentElement?.parentElement]
      .filter(Boolean)
      .some(el => String((el as Element).getAttribute('class') ?? '').includes('feedback-error'));
    expect(painted, 'the tag is not painted from the error feedback token').toBe(true);
  });

  it('shows no licence tag on a production licence', () => {
    renderTopBar();
    expect(screen.queryByText('EE DEV LICENSE')).toBeNull();
  });

  it('pins the bar to the top of the viewport, never sticky', () => {
    renderTopBar();
    // The library ships no positioning on purpose; the doctrine it documents is
    // that the bar is ALWAYS fixed and NEVER sticky, and supplying that is the
    // consumer's job. A regression to `sticky` (or to the library's default
    // `relative`) is exactly what this asserts against.
    expect(getBar().style.position).toBe('fixed');
    expect(getBar().style.top).toBe('0px');
    expect(getBar().style.right).toBe('0px');
  });

  it('offsets the bar by the expanded navigation width', () => {
    localStorage.setItem(NAV_OPEN_STORAGE_KEY, 'true');
    renderTopBar();
    expect(getBar().style.left).toBe(`${OPEN_BAR_WIDTH}px`);
  });

  it('offsets the bar by the collapsed navigation width', () => {
    localStorage.setItem(NAV_OPEN_STORAGE_KEY, 'false');
    renderTopBar();
    expect(getBar().style.left).toBe(`${SMALL_BAR_WIDTH}px`);
  });

  it('follows the navigation widths declared by the design system Navbar', () => {
    // The library's rail is w-45 / w-12 on a 4px spacing scale. A previous
    // pilot found both products declaring 55 for the collapsed rail against
    // the library's real 48, which left a 7px gap under the bar's left edge.
    expect(OPEN_BAR_WIDTH).toBe(180);
    expect(SMALL_BAR_WIDTH).toBe(48);
  });

  it('keeps the customer-configured background colour driving the bar gradient', () => {
    // STEP 6b regression guard. The bar's gradient is admin-configurable
    // (platform background_color -> palette.background.gradient). The library
    // paints itself from its own --gradient-default, so if this override is
    // ever dropped the bar silently reverts to Filigran's default colours for
    // every customer who set their own - a functional loss that looks like
    // nothing more than a slightly different shade.
    renderTopBar();
    const gradient = getBar().style.getPropertyValue('--gradient-default');
    expect(gradient).toContain(GRADIENT_START);
    expect(gradient).toContain(GRADIENT_END);
  });

  it('passes the gradient stops opaque, letting the library apply the glass opacity', () => {
    // The legacy bar faded its own stops to 90%; the library paints its
    // gradient layer at Figma's 94%. Pre-fading here would apply the
    // transparency twice and darken the bar.
    renderTopBar();
    expect(getBar().style.getPropertyValue('--gradient-default')).not.toContain('rgba');
  });

  it('bounds the search field between 200px and 500px', () => {
    // Arbitrated: the field runs from a 200px floor to a
    // 500px ceiling. The library's growing group caps at 400px, BELOW that
    // ceiling, so grow="unbounded" stays the supported way to say "I supply my
    // own window" - and the window is this one.
    renderTopBar();
    // The window sits on the group, not on the SearchField instance: the
    // component forwards `className` to its wrapper but spreads its remaining
    // props - `style` included - onto the inner <input>, so an inline width
    // there would size the text box instead of the field. Asserted on the
    // group the product owns; the field fills it through `fullWidth`.
    const group = screen.getByRole('search').parentElement as HTMLElement;
    expect(group.style.minWidth).toBe('200px');
    expect(group.style.maxWidth).toBe('500px');
  });

  it('lets the field fill its window', () => {
    // The window only bounds the rendered field if the field actually spans
    // it. Without `fullWidth` the library's fixed `w-55` (220px) would sit
    // inside the group and the ceiling would never be reached.
    renderTopBar();
    expect(screen.getByRole('search').className.split(/\s+/)).toContain('w-full');
  });

  it('does not let the bar stretch to full width while it is offset', () => {
    // RFC trap: `w-full` is 100% of the CONTAINING BLOCK and does not conflict
    // with `left` in tailwind-merge, so the library's default `fullWidth`
    // would push the bar off the right edge by exactly the navigation width.
    // The overflow is invisible in a headless DOM, so assert the cause.
    renderTopBar();
    expect(getBar().className.split(/\s+/)).not.toContain('w-full');
  });

  it('exposes exactly one banner landmark', () => {
    renderTopBar();
    expect(screen.getAllByRole('banner')).toHaveLength(1);
  });

  it('keeps the account menu reachable', () => {
    renderTopBar();
    expect(screen.getByLabelText('account-menu')).toBeTruthy();
  });

  // Scope rule (designer, round 2): where the library ships a component, the
  // pilot uses it. The bar was Header + HeaderGroup from the library with a
  // MUI interior; these assert the interior converted too, because a MUI
  // control keeps MUI's focus/hover/selected states no matter how it is
  // painted - which was the designer's actual complaint.
  describe('Design system adoption', () => {
    it('searches through the library SearchField', () => {
      renderTopBar();
      // `searchFieldVariants` styles the wrapper the library exposes as
      // role="search", not the inner input.
      expectLibrarySearchField(screen.getByRole('search'), 'platform search');
    });

    it('uses the library icon button for the account menu', () => {
      renderTopBar();
      expectLibraryIconButton(screen.getByLabelText('account-menu'), 'account menu');
    });

    it('leaves no MUI control anywhere in the bar', () => {
      // The single maintained exception is icon GLYPHS (same arbitration as
      // the Navbar pilot), which this helper ignores.
      renderTopBar();
      expectNoMuiControls(getBar(), 'top bar');
    });
  });
});
