import { NavbarItem } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import logoFiligran from '../../../../static/images/logo_filigran_full.svg';
import { fileUri } from '../../../../utils/Environment';
import { useFormatter } from '../../../i18n';

interface Props { collapsed: boolean }

// Geometry is inline, not utility classes: the product has no Tailwind build,
// so only the utilities the design system itself emits exist at runtime.
// Sizing and `object-*` are not among them and would be silently inert.
const WORDMARK_HEIGHT = 12;

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
                // The emblem is the left edge of the wordmark asset, so a square
                // box cropped from the left shows it alone — no second asset to
                // keep in sync with the brand.
                width: WORDMARK_HEIGHT,
                height: WORDMARK_HEIGHT,
                objectFit: 'cover',
                objectPosition: 'left center',
                // A collapsed row is 48px wide but its content box is 46px:
                // NavbarItem reserves 2px for the selected indicator. The
                // library offsets its own icons by 2px to compensate; without
                // the same offset the emblem sits 1px off the icon axis.
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
