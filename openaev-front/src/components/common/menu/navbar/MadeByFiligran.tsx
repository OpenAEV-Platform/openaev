import { NavbarItem } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import logoFiligran from '../../../../static/images/logo_filigran_full.svg';
import { fileUri } from '../../../../utils/Environment';
import { useFormatter } from '../../../i18n';

interface Props { collapsed: boolean }

/**
 * Geometry is expressed inline, not with utility classes. The product has no
 * Tailwind build: the only utilities that exist at runtime are those the
 * design system's own components happen to emit into its stylesheet. Sizing
 * and `object-*` utilities are not among them, so a class here would be
 * silently inert. Design *tokens* (the caption typography below) are safe —
 * they are what the library publishes on purpose.
 */
const WORDMARK_HEIGHT = 12;

/**
 * "Made by Filigran" footer row: the muted label (expanded only) then the
 * wordmark. Typography mirrors the library's own signature row (caption token
 * set, `text-default-secondary`, 12px wordmark) rather than ad-hoc sizes, so it
 * stays aligned with the design system when those tokens move.
 *
 * Collapsed, it behaves like the header's logo: the label disappears and only
 * the Filigran emblem remains. The emblem is not a separate asset — the
 * wordmark SVG starts with it, so a 12px square box with `object-cover` and a
 * left origin crops the lettering away. That is what the legacy row did, and
 * it keeps a single asset to maintain. The accessible name is carried by
 * `aria-label` and is therefore identical in both states.
 */
const MadeByFiligran: FunctionComponent<Props> = ({ collapsed }) => {
  const { t } = useFormatter();
  return (
    <NavbarItem asChild tooltipLabel={t('By Filigran')}>
      <button
        type="button"
        aria-label="By Filigran"
        onClick={() => window.open('https://filigran.io/', '_blank', 'noopener,noreferrer')}
      >
        {!collapsed && (
          <span className="text-default-secondary shrink-0 text-content-caption font-content-caption leading-content-caption tracking-content-caption">
            {t('Made by')}
          </span>
        )}
        <img
          alt="Filigran"
          src={fileUri(logoFiligran)}
          className="shrink-0"
          style={collapsed
            ? {
                // The emblem is the left edge of the wordmark asset, so a
                // square box cropped from the left shows it alone — no second
                // asset to keep in sync with the brand.
                width: WORDMARK_HEIGHT,
                height: WORDMARK_HEIGHT,
                objectFit: 'cover',
                objectPosition: 'left center',
                // A collapsed row is 48px wide but its content box is 46px:
                // `NavbarItem` reserves a 2px left border for the selected
                // indicator. Centring inside that box lands 1px off the rail's
                // optical centre, so the library's own rows offset the icon by
                // 2px on the right. Same trick here, so the emblem sits on the
                // exact axis of the icons above it.
                marginRight: 2,
              }
            : {
                height: WORDMARK_HEIGHT,
                width: 'auto',
              }}
        />
      </button>
    </NavbarItem>
  );
};

export default MadeByFiligran;
