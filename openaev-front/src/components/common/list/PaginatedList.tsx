import { ListItem, ListItemButton, ListItemIcon, ListItemText, type SvgIconProps } from '@mui/material';
import { type ComponentType, type CSSProperties, type ReactNode } from 'react';

import useBodyItemsStyles from '../queryable/style/style';
import { type Header } from '../SortHeadersList';

interface Props<T> {
  Icon: ComponentType<SvgIconProps>;
  secondaryAction?: (item: T) => ReactNode;
  headers: Header[];
  items: T[];
  rowKey: keyof T;
  onRowClick?: (item: T) => void;
  itemWidth?: Record<string, CSSProperties>;
}

const PaginatedList = <T, >({ Icon, secondaryAction, headers, items, rowKey, onRowClick, itemWidth = {} }: Props<T>) => {
  const bodyItemsStyles = useBodyItemsStyles();
  return (
    <>
      {items.map((item: T) => {
        const rowContent = (
          <>
            {Icon && <ListItemIcon><Icon color="primary" /></ListItemIcon>}
            <ListItemText primary={(
              <div style={bodyItemsStyles.bodyItems}>
                {headers.map(header => (
                  <div
                    key={header.field}
                    style={{
                      ...bodyItemsStyles.bodyItem,
                      ...{ width: itemWidth?.[header.field]?.width },
                    }}
                  >
                    {header.value?.(item)}
                  </div>
                ))}
              </div>
            )}
            />
          </>
        );

        return (
          <ListItem
            key={String(item[rowKey])}
            divider
            disablePadding={!!onRowClick}
            secondaryAction={secondaryAction?.(item)}
          >
            {onRowClick
              ? (
                  <ListItemButton style={{ height: 50 }} onClick={() => onRowClick(item)}>
                    {rowContent}
                  </ListItemButton>
                )
              : rowContent}
          </ListItem>
        );
      })}
    </>
  );
};

export default PaginatedList;
