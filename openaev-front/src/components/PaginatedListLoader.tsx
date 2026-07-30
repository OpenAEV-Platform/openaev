import { MoreVert } from '@mui/icons-material';
import {
  Checkbox,
  IconButton,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Skeleton,
  type SvgIconProps,
} from '@mui/material';
import { type ComponentType, type CSSProperties, type FunctionComponent } from 'react';

import { type Header } from './common/SortHeadersList';

interface Props {
  headers: Header[];
  headerStyles: Record<string, CSSProperties>;
  Icon: ComponentType<SvgIconProps>;
  height?: number;
  number?: number;
  /**
   * Mirrors the leading bulk-selection checkbox column of the real rows so
   * the skeleton stays perfectly aligned with the header row and the loaded
   * list. Pass the exact same condition that gates the checkboxes on the
   * real rows (e.g. `canManage`, `!!entityToggle`).
   */
  withCheckbox?: boolean;
}

const PaginatedListLoader: FunctionComponent<Props> = ({
  headers,
  headerStyles,
  Icon,
  height = 50,
  number = 21,
  withCheckbox = false,
}) => {
  return (
    [...Array(number)].map((_, key) => (
      <ListItem
        key={key}
        disablePadding
        divider
        secondaryAction={(
          <IconButton
            size="large"
            disabled
          >
            <MoreVert color="disabled" />
          </IconButton>
        )}
      >
        <ListItemButton
          style={{
            height,
            pointerEvents: 'none',
          }}
        >
          {withCheckbox && (
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={false}
                disabled
                disableRipple
              />
            </ListItemIcon>
          )}
          <ListItemIcon>
            <Icon color="disabled" />
          </ListItemIcon>
          <ListItemText
            primary={(
              <div style={{ display: 'flex' }}>
                {headers.map(header => (
                  <div
                    key={header.field}
                    style={{
                      ...headerStyles[header.field],
                      paddingRight: 10,
                    }}
                  >
                    <Skeleton height={40} />
                  </div>
                ))}
              </div>
            )}
          />
        </ListItemButton>
      </ListItem>
    ))
  );
};

export default PaginatedListLoader;
