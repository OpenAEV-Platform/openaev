import { Box, Drawer, ListItemIcon, ListItemText, MenuItem, MenuList } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router';

import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import useAuth from '../../../utils/hooks/useAuth';
import { isNotEmptyField } from '../../../utils/utils';
import { useFormatter } from '../../i18n';

// The library publishes the header height; the fallback applies only if its stylesheet failed to load.
const HEADER_HEIGHT = 'var(--fds-header-height, 68px)';
// The shell's own bottom padding (admin/Index.tsx): a sticky rail cannot leave
// its container, so a full-height rail would be shoved 24px up at the end of the scroll.
const PAGE_BOTTOM_PADDING = 24;
/** The rail itself, and the breathing room between it and the content it sits beside. */
const RAIL_WIDTH = 200;
const RAIL_GAP = 16;
/** What a container must reserve on its right for a sticky rail. */
export const STICKY_RAIL_GUTTER = RAIL_WIDTH + RAIL_GAP;
/** The tab bar's own bottom margin, which the rail climbs back over to meet its border. */
const TAB_BAR_GAP = 16;

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
  /**
   * 'drawer' pins the rail to the page under the header bar; 'sticky' starts it
   * where it is rendered and sticks to the header on scroll, in a 200px gutter
   * its container must reserve.
   */
  variant?: 'drawer' | 'sticky';
}

const RightMenu: FunctionComponent<Props> = ({ entries, header, variant = 'drawer' }) => {
  const location = useLocation();
  const theme = useTheme();
  const { t } = useFormatter();

  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);
  // Banner plus header bar, kept as a CSS expression so the library's height stays the single source.
  const topOffset = `calc(${bannerHeightNumber}px + ${HEADER_HEIGHT})`;

  // One layer above the page: the nav token resolves to the page ground itself
  // (#070d18 on both), which left the bar indistinguishable from the content.
  const surface = {
    backgroundColor: 'var(--bg-elevation-default-layer-1)',
    backgroundImage: 'none',
    borderLeft: '1px solid var(--border-elevation-subtle-soft)',
    width: RAIL_WIDTH,
  };

  const content = (
    <div>
      {header}
      <MenuList component="nav" sx={{ paddingTop: 0.5 }}>
        {entries.map((entry, idx) => {
          // Also highlight on nested routes, ignoring the entry's query string.
          const targetPath = (entry.activePath ?? entry.path).split('?')[0];
          const isCurrentTab = location.pathname === targetPath
            || location.pathname.startsWith(`${targetPath}/`);
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
  );

  if (variant === 'sticky') {
    return (
      <Box
        component="aside"
        sx={{
          ...surface,
          // Floated, so the content beside it is not pushed down, and dropped
          // into the gutter its container reserves.
          float: 'right',
          marginRight: `-${STICKY_RAIL_GUTTER}px`,
          // Up over the tab bar's bottom margin, so the rail starts on its border.
          marginTop: `-${TAB_BAR_GAP}px`,
          // Sticky, not fixed: the browser scrolls it with the page and pins it
          // under the header by itself, with none of the lag of a scroll
          // listener. This needs <main> to clip rather than hide its overflow.
          position: 'sticky',
          top: topOffset,
          height: `calc(100vh - ${topOffset} - ${PAGE_BOTTOM_PADDING}px)`,
        }}
      >
        {content}
      </Box>
    );
  }

  return (
    <Drawer
      variant="permanent"
      anchor="right"
      sx={{
        'width': 200,
        '& .MuiDrawer-paper': {
          ...surface,
          top: topOffset,
          height: `calc(100% - ${topOffset})`,
        },
      }}
    >
      {content}
    </Drawer>
  );
};

export default RightMenu;
