import { Box, Chip, Grid, List, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type ReactElement, useMemo } from 'react';

import { truncate } from '../../utils/String';

interface SelectListIcon { value: () => ReactElement }

interface SelectListHeader<T> {
  field: string;
  value: (value: T) => ReactElement | string;
  width: number;
}

export interface SelectListElements<T> {
  icon: SelectListIcon;
  headers: SelectListHeader<T>[];
}

interface Props<T, V> {
  values: T[];
  selectedValues: (T | V)[];
  elements: SelectListElements<T>;
  onSelect: (id: string, value: T) => void;
  onDelete: (id: string) => void;
  paginationComponent: ReactElement;
  buttonComponent?: ReactElement;
  getId: (element: T | V) => string;
  getName: (element: T | V) => string;
}

const SelectList = <T extends object, V extends object = T>({
  values,
  selectedValues,
  elements,
  onSelect,
  onDelete,
  paginationComponent,
  buttonComponent,
  getId,
  getName,
}: Props<T, V>) => {
  const selectedIds = useMemo(
    () => selectedValues.map(v => getId(v)),
    [selectedValues],
  );

  return (
    <>
      {paginationComponent}
      <Grid container spacing={3}>
        <Grid size={{ xs: 8 }}>
          <List>
            {values.map((value) => {
              const id = getId(value);
              const disabled = selectedIds.includes(id);
              return (
                <ListItemButton
                  key={id}
                  disabled={disabled}
                  divider
                  onClick={() => onSelect(id, value)}
                >
                  <ListItemIcon>
                    {elements.icon.value()}
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <Box sx={{ display: 'flex' }}>
                        {elements.headers.map(header => (
                          <Box
                            key={header.field}
                            sx={{
                              height: 20,
                              fontSize: 13,
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              paddingRight: 1,
                              width: `${header.width}%`,
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
        </Grid>
        <Grid size={{ xs: 4 }}>
          <Box
            sx={{
              minHeight: '100%',
              padding: 2,
              border: '1px dashed rgba(255, 255, 255, 0.3)',
            }}
          >
            {selectedValues.map((selectedValue) => {
              const id = getId(selectedValue);
              const name = getName(selectedValue);
              return (
                <Chip
                  key={id}
                  onDelete={() => onDelete(id)}
                  label={truncate(name, 22)}
                  sx={{ margin: '0 10px 10px 0' }}
                />
              );
            })}
          </Box>
        </Grid>
      </Grid>
    </>
  );
};

export default SelectList;
