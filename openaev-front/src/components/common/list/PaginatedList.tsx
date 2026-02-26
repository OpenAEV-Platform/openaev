import { ListItem, ListItemButton, ListItemIcon, ListItemText, type SvgIconProps } from '@mui/material';
import { type ComponentType, type ReactNode } from 'react';

import useBodyItemsStyles from '../queryable/style/style';
import { type Header } from '../SortHeadersList';

interface Props<T> {
  Icon: ComponentType<SvgIconProps>;
  secondaryAction?: (item: T) => ReactNode;
  headers: Header[];
  items: T[];
  rowKey: keyof T;
}

const PaginatedList = <T, >({ Icon, secondaryAction, headers, items, rowKey }: Props<T>) => {
  const bodyItemsStyles = useBodyItemsStyles();

  return (
    <>
      {items.map((item: T) => (
        <ListItem
          key={String(item[rowKey])}
          divider
          disablePadding
          secondaryAction={secondaryAction?.(item)}
        >
          <ListItemButton style={{ height: 50 }}>
            {Icon && <ListItemIcon><Icon color="primary" /></ListItemIcon>}
            <ListItemText primary={(
              <div style={bodyItemsStyles.bodyItems}>
                {headers.map(header => (
                  <div
                    key={header.field}
                    style={{ ...bodyItemsStyles.bodyItem }}
                  >
                    {header.value?.(item)}
                  </div>
                ))}
              </div>
            )}
            />
          </ListItemButton>
        </ListItem>
      ))}
    </>
  );
};

export default PaginatedList;
