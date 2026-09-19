import { Drawer, ListItemIcon, ListItemText, MenuItem, MenuList } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router';

import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import useAuth from '../../../utils/hooks/useAuth';
import { isNotEmptyField } from '../../../utils/utils';
import { useFormatter } from '../../i18n';

// The library publishes the header height; the fallback applies only if its stylesheet failed to load.
const HEADER_HEIGHT = 'var(--fds-header-height, 68px)';

export interface RightMenuEntry {
  path: string;
  icon: () => ReactElement;
  label: string;
  number?: number;
  chip?: ReactElement;
  onClick?: () => void;
  /** When set, the entry is highlighted while the URL path matches this instead of `path`. */
  activePath?: string;
}

interface Props {
  entries: RightMenuEntry[];
  /** Optional element rendered above the entries (e.g. a scope/context switcher). */
  header?: ReactNode;
}

const RightMenu: FunctionComponent<Props> = ({ entries, header }) => {
  const location = useLocation();
  const theme = useTheme();
  const { t } = useFormatter();

  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  // Banner plus header bar, kept as a CSS expression so the library's height stays the single source.
  const topOffset = `calc(${bannerHeightNumber}px + ${HEADER_HEIGHT})`;

  return (
    <Drawer
      variant="permanent"
      anchor="right"
      sx={{
        'width': 200,
        '& .MuiDrawer-paper': {
          // One layer above the page, so the panel reads as a panel. The nav
          // token resolves to the page ground itself (#070d18 on both), which
          // left the bar indistinguishable from the content beside it and only
          // a 1px hairline to separate them — the sibling product's bar is
          // lighter than its page, which is the same step said in tokens.
          backgroundColor: 'var(--bg-elevation-default-layer-1)',
          backgroundImage: 'none',
          // The elevation carries the separation; the docked hairline MUI adds
          // on top of it reads as a second, harder edge.
          borderLeft: 'none',
          width: 200,
          top: topOffset,
          height: `calc(100% - ${topOffset})`,
        },
      }}
    >
      <div>
        {header}
        <MenuList component="nav" sx={{ paddingTop: 0.5 }}>
          {entries.map((entry, idx) => {
            // Highlight the entry on its own route AND on any nested route
            // (e.g. a detail/overview page like ".../users/{id}"), ignoring any
            // query string on the entry's target path.
            const targetPath = (entry.activePath ?? entry.path).split('?')[0];
            const isCurrentTab = location.pathname === targetPath
              || location.pathname.startsWith(`${targetPath}/`);
            // Icon styling mirrors OpenCTI's NavToolbarMenu: compact 16px glyph,
            // muted tertiary color when idle, lighter + full opacity when active.
            const iconColor = isCurrentTab ? theme.palette.text.light : theme.palette.text.tertiary;
            const iconOpacity = isCurrentTab ? 1 : 0.5;
            return (
              <MenuItem
                key={idx}
                component={Link}
                to={entry.onClick ? '#' : entry.path}
                selected={isCurrentTab}
                onClick={entry.onClick
                  ? (e: React.MouseEvent) => {
                      e.preventDefault();
                      entry.onClick?.();
                    }
                  : undefined}
                sx={{
                  'paddingRight': 0,
                  '& .MuiListItemText-primary': { fontSize: 14 },
                }}
              >
                <ListItemIcon
                  sx={{
                    'minWidth': '0px!important',
                    'mr': 1,
                    'opacity': iconOpacity,
                    'color': iconColor,
                    '& svg': { fontSize: '16px!important' },
                  }}
                >
                  {entry.icon()}
                </ListItemIcon>
                <ListItemText primary={isNotEmptyField(entry.number) ? `${t(entry.label)} (${entry.number})` : t(entry.label)} />
                {entry.chip && <>{entry.chip}</>}
              </MenuItem>
            );
          })}
        </MenuList>
      </div>
    </Drawer>
  );
};

export default RightMenu;
