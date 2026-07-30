import { alpha, type Theme } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SystemStyleObject } from '@mui/system';
import type { CSSProperties } from 'react';

// Shared styling for the left navigation, aligned 1:1 with OpenCTI's LeftBarItem
// (opencti-front/src/private/components/nav/LeftBarItem.tsx): 36px rows, a 2px
// primary left-border + translucent-primary background on the active row, dimmed
// 16px icons, and a leftBar.hover background. `menuItemSx` is applied to every
// top-level row (single, group, toggle) so the whole bar reads identically.
const useLeftMenuStyle: () => {
  listItemIcon: CSSProperties;
  listItemText: CSSProperties;
  // Concrete style object (not the broad SxProps union) so consumers can
  // compose it in array form: sx={[leftMenuStyle.menuItemSx, ...overrides]}.
  menuItemSx: SystemStyleObject<Theme>;
} = () => {
  const theme = useTheme();

  return ({
    listItemIcon: { minWidth: 0 },
    listItemText: {
      fontWeight: 500,
      fontSize: 14,
      color: theme.palette.leftBar?.text ?? theme.palette.text.primary,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap',
    },
    menuItemSx: {
      'height': 36,
      'paddingLeft': 2,
      'paddingRight': 1,
      'paddingTop': 0,
      'paddingBottom': 0,
      'borderLeft': '2px solid transparent',
      '& .MuiListItemIcon-root': {
        minWidth: 0,
        marginRight: 1,
        color: theme.palette.text.tertiary,
        opacity: 0.5,
      },
      '& .MuiListItemIcon-root svg': { fontSize: 16 },
      '& .MuiListItemText-root': { paddingTop: '1px' },
      '&:hover': { backgroundColor: theme.palette.leftBar?.hover },
      // Neutralize the app-wide MuiMenuItem selected override (inset box-shadow +
      // strong tint) and use OpenCTI's border-left + subtle tint instead.
      '&.Mui-selected': {
        boxShadow: 'none',
        borderLeftColor: theme.palette.primary.main,
        backgroundColor: alpha(theme.palette.primary.main, 0.1),
      },
      '&.Mui-selected:hover': {
        boxShadow: 'none',
        backgroundColor: theme.palette.action.selected,
      },
      '&.Mui-selected .MuiListItemIcon-root': { color: theme.palette.text.light },
    },
  });
};

export default useLeftMenuStyle;
