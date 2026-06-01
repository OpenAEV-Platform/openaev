import { Box, Checkbox, List, ListItemButton, ListItemIcon, ListItemText, Skeleton } from '@mui/material';
import { type ReactElement, useMemo } from 'react';

export interface ClickableListHeader<T> {
  field: string;
  value: (value: T) => ReactElement | string;
  width: number;
}

export interface ClickableListElements<T> {
  icon: { value: () => ReactElement };
  headers: ClickableListHeader<T>[];
}

interface ClickableListProps<T> {
  values: T[];
  selectedIds: string[];
  elements: ClickableListElements<T>;
  onSelect: (id: string, value: T) => void;
  onDeselect?: (id: string) => void;
  paginationComponent: ReactElement;
  buttonComponent?: ReactElement;
  getId: (element: T) => string;
  isLoading: boolean;
}

const ClickableList = <T extends object>({
  values,
  selectedIds,
  elements,
  onSelect,
  onDeselect,
  paginationComponent,
  buttonComponent,
  getId,
  isLoading,
}: ClickableListProps<T>) => {
  const selectedIdSet = useMemo(
    () => new Set(selectedIds),
    [selectedIds],
  );

  return (
    <>
      {paginationComponent}
      {isLoading ? <Skeleton height={40} /> : (
        <List>
          {values.map((value) => {
            const id = getId(value);
            const isSelected = selectedIdSet.has(id);
            return (
              <ListItemButton
                key={id}
                disabled={isSelected && !onDeselect}
                divider
                onClick={() => (isSelected && onDeselect ? onDeselect(id) : onSelect(id, value))}
              >
                <ListItemIcon>
                  {onDeselect
                    ? (
                        <Checkbox
                          edge="start"
                          checked={isSelected}
                          tabIndex={-1}
                          disableRipple
                        />
                      )
                    : elements.icon.value()}
                </ListItemIcon>
                <ListItemText
                  primary={(
                    <Box sx={{ display: 'flex' }}>
                      {elements.headers.map(header => (
                        <Box
                          key={header.field}
                          sx={{
                            height: 20,
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            width: '100%',
                          }}
                        >
                          {header.value(value)}
                        </Box>
                      ))}
                    </Box>
                  )}
                />
              </ListItemButton>
            );
          })}
          {buttonComponent}
        </List>
      )}
    </>
  );
};

export default ClickableList;
