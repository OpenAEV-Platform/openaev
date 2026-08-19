import { Text } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';

/**
 * The library `Paper` header row, reproduced for surfaces that do NOT have a
 * Paper — and which must not gain one.
 *
 * Some sections carry no surface of their own: their content sits on the page
 * background. Giving them a Paper just to get its header would add a border and
 * a background they never had. So the header is aligned instead of converted,
 * the same arbitration as `libSurfaceBorder`: when converting would add or
 * remove something, align the visual rather than convert.
 *
 * **This file mirrors the library's own header and must follow it.** The
 * library composes it as
 * `flex h-6 items-center gap-2` inside a `flex flex-col gap-2` wrapper, with
 * the title in `content-compact text-default-secondary`.
 * `LibHeaderRow.test.tsx` renders both and fails if either side moves.
 *
 * `content-compact` is used as ONE composite class, never as the four
 * `text-`/`font-`/`leading-`/`tracking-` utilities: those four carry no
 * font-weight, so the title would inherit whatever wraps it. The library
 * recorded that confusion twice before writing it down.
 *
 * ## What the guard catches, and what it does not
 *
 * The guard renders the real library `Paper` and reads the classes it puts on
 * its header, so it DOES fail on a library bump that renames or drops them.
 * Verified by patching the built chunk the tests load: `h-6` changed to `h-7`
 * upstream turns the guard red.
 *
 * **Known limit: it compares class NAMES, not what those classes compute.** If
 * the library keeps `h-6` while the spacing scale behind it changes, the guard
 * stays green and the two numbers below drift. The typography is safe from this
 * — both sides use the same composite class, so a change to its definition
 * moves them together — but `LIB_HEADER_ROW_HEIGHT` and `LIB_HEADER_GAP` are
 * plain numbers here against classes there.
 *
 * Nothing in jsdom can close that: it does not apply the library stylesheet.
 * Only a measurement in a real browser can, and that is what the migration
 * boards do at each bump. Written down rather than left as false security.
 */
export const LIB_HEADER_ROW_HEIGHT = 24;
export const LIB_HEADER_GAP = 8;
/** The typography the library puts on its header row. */
export const LIB_HEADER_TITLE_CLASSES = 'content-compact text-default-secondary';

interface Props {
  title: ReactNode;
  action?: ReactNode;
  children: ReactNode;
}

const LibHeaderRow: FunctionComponent<Props> = ({ title, action, children }) => (
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    gap: LIB_HEADER_GAP,
    height: '100%',
  }}
  >
    <div
      data-testid="lib-header-row"
      style={{
        display: 'flex',
        height: LIB_HEADER_ROW_HEIGHT,
        alignItems: 'center',
        gap: LIB_HEADER_GAP,
        justifyContent: 'space-between',
      }}
    >
      <Text variant="content-compact" className={LIB_HEADER_TITLE_CLASSES}>
        {title}
      </Text>
      {action != null && (
        <div style={{
          display: 'flex',
          flexShrink: 0,
          alignItems: 'center',
          gap: LIB_HEADER_GAP,
        }}
        >
          {action}
        </div>
      )}
    </div>
    {children}
  </div>
);

export default LibHeaderRow;
