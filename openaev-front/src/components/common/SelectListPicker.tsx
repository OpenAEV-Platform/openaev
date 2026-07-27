import {
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Skeleton,
} from '@mui/material';
import { type Breakpoint } from '@mui/material/styles';
import { type CSSProperties, type ReactElement, type ReactNode, useMemo, useState } from 'react';

import { useFormatter } from '../i18n';
import ActionButtons from './ActionButtons';
import Drawer from './Drawer';
import SortHeadersComponentV2 from './queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from './queryable/sort/SortHelpers';
import useBodyItemsStyles from './queryable/style/style';
import { type Header } from './SortHeadersList';
import Transition from './Transition';

interface SelectListPickerIcon<T> { value: (element: T) => ReactElement }

interface SelectListPickerHeader<T> {
  field: string;
  /** Column header label (i18n key). */
  label: string;
  /** Server-side sortable field; requires `sortHelpers` to take effect. */
  isSortable?: boolean;
  value: (value: T) => ReactElement | string;
  width: number;
}

export interface SelectListPickerElements<T> {
  icon: SelectListPickerIcon<T>;
  headers: SelectListPickerHeader<T>[];
}

interface Props<T> {
  open: boolean;
  onClose: () => void;
  onSubmit: () => void;
  title: string;
  submitLabel?: string;
  /**
   * Render as a Dialog instead of a Drawer. MANDATORY when the picker opens
   * above another drawer (inject form, team players panel, asset group
   * management...): the design system never stacks a drawer over a drawer.
   */
  inline?: boolean;
  /** Max width of the dialog when `inline` (defaults to 'lg' app-wide). */
  dialogMaxWidth?: Breakpoint;
  /** Search input + filters + pagination controls (e.g. PaginationComponentV2). */
  headerComponent?: ReactNode;
  /** Rendered next to the title (e.g. a "Select all" action). */
  headerActions?: ReactNode;
  /** Creation button rendered top-right in the header (e.g. CreateTeam). */
  buttonComponent?: ReactNode;
  values: T[];
  elements: SelectListPickerElements<T>;
  /**
   * Server-side sorting (pass `queryableHelpers.sortHelpers`). When omitted,
   * sortable columns fall back to client-side sorting on the raw field value.
   */
  sortHelpers?: SortHelpers;
  /** Ids currently selected (checked, toggleable). */
  selectedIds: string[];
  /** Ids that are checked but locked (already attached, add-only flows). */
  lockedIds?: string[];
  onToggle: (id: string, value: T) => void;
  getId: (element: T) => string;
  isLoading?: boolean;
  submitDisabled?: boolean;
  /** Drawer width when not `inline`. */
  variant?: 'half' | 'full';
  containerTestId?: string;
}

/**
 * Design-system entity picker: a search/filter/pagination header above a real
 * list (column headers, optional sorting, compact rows, trailing checkboxes).
 * Rendered as a right drawer at page level, or as a dialog (`inline`) when
 * opened above another drawer. Replaces the legacy "list + chips basket"
 * selection dialogs.
 */
const SelectListPicker = <T extends object>({
  open,
  onClose,
  onSubmit,
  title,
  submitLabel,
  inline = false,
  dialogMaxWidth = 'lg',
  headerComponent,
  headerActions,
  buttonComponent,
  values,
  elements,
  sortHelpers,
  selectedIds,
  lockedIds = [],
  onToggle,
  getId,
  isLoading = false,
  submitDisabled = false,
  variant = 'half',
  containerTestId,
}: Props<T>) => {
  const { t } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();

  const selectedCount = useMemo(() => {
    const ids = new Set([...selectedIds, ...lockedIds]);
    return ids.size;
  }, [selectedIds, lockedIds]);

  // Header slot: selected count + secondary actions (e.g. "Select all") + the
  // creation button, rendered top-right next to the title in both modes.
  const headerRightSlot = (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        flexShrink: 0,
      }}
    >
      <Chip
        size="small"
        variant="outlined"
        color={selectedCount > 0 ? 'primary' : 'default'}
        label={t('{count} selected', { count: selectedCount })}
      />
      {headerActions}
      {buttonComponent}
    </Box>
  );

  // Client-side sorting fallback for pickers without queryable pagination:
  // sortable columns sort locally on the raw field value.
  const [localSortBy, setLocalSortBy] = useState('');
  const [localSortAsc, setLocalSortAsc] = useState(true);
  const localSortHelpers: SortHelpers = {
    handleSort: (field: string) => {
      if (localSortBy === field) {
        setLocalSortAsc(!localSortAsc);
      } else {
        setLocalSortBy(field);
        setLocalSortAsc(true);
      }
    },
    handleDirectedSort: (field: string, asc: boolean) => {
      setLocalSortBy(field);
      setLocalSortAsc(asc);
    },
    getSortBy: () => localSortBy,
    getSortAsc: () => localSortAsc,
  };

  const sortedValues = useMemo(() => {
    if (sortHelpers || !localSortBy) {
      return values;
    }
    const compare = (a: T, b: T) => {
      const rawA = (a as Record<string, unknown>)[localSortBy];
      const rawB = (b as Record<string, unknown>)[localSortBy];
      return String(rawA ?? '').localeCompare(String(rawB ?? ''), undefined, {
        numeric: true,
        sensitivity: 'base',
      });
    };
    return [...values].sort((a, b) => (localSortAsc ? compare(a, b) : compare(b, a)));
  }, [values, sortHelpers, localSortBy, localSortAsc]);

  const sortableHeaders: Header[] = useMemo(() => elements.headers.map(header => ({
    field: header.field,
    label: header.label,
    isSortable: !!header.isSortable,
  })), [elements.headers]);

  const inlineStylesHeaders: Record<string, CSSProperties> = useMemo(
    () => Object.fromEntries(elements.headers.map(header => [header.field, { width: `${header.width}%` }])),
    [elements.headers],
  );

  const cellStyle = (width: number): CSSProperties => ({
    ...bodyItemsStyles.bodyItem,
    display: 'flex',
    alignItems: 'center',
    width: `${width}%`,
  });

  const listContent = (
    <>
      {headerComponent}
      <List dense sx={{ paddingTop: 1 }}>
        {/* 44px = dense row right padding (16) + small checkbox with 4px
            padding (28), so header cells align exactly with body cells. */}
        <ListItem
          dense
          divider
          sx={{ paddingRight: '44px' }}
        >
          <ListItemIcon sx={{ minWidth: 32 }} />
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={sortableHeaders}
                inlineStylesHeaders={inlineStylesHeaders}
                sortHelpers={sortHelpers ?? localSortHelpers}
              />
            )}
          />
        </ListItem>
        {isLoading ? (
          [...Array(8)].map((_, index) => (
            <ListItemButton
              key={index}
              dense
              divider
              style={{ pointerEvents: 'none' }}
            >
              <ListItemIcon sx={{ minWidth: 32 }}>
                <Skeleton variant="circular" width={22} height={22} />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <Box style={bodyItemsStyles.bodyItems}>
                    {elements.headers.map(header => (
                      <Box
                        key={header.field}
                        style={cellStyle(header.width)}
                      >
                        <Skeleton width="70%" height={20} />
                      </Box>
                    ))}
                  </Box>
                )}
              />
              <Skeleton variant="rectangular" width={18} height={18} />
            </ListItemButton>
          ))
        ) : (
          <>
            {sortedValues.map((value) => {
              const id = getId(value);
              const locked = lockedIds.includes(id);
              const checked = locked || selectedIds.includes(id);
              return (
                <ListItemButton
                  key={id}
                  dense
                  divider
                  disabled={locked}
                  onClick={() => onToggle(id, value)}
                >
                  <ListItemIcon sx={{ minWidth: 32 }}>
                    {elements.icon.value(value)}
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <Box style={bodyItemsStyles.bodyItems}>
                        {elements.headers.map(header => (
                          <Box
                            key={header.field}
                            style={cellStyle(header.width)}
                          >
                            {header.value(value)}
                          </Box>
                        ))}
                      </Box>
                    )}
                  />
                  <Checkbox
                    size="small"
                    checked={checked}
                    disableRipple
                    tabIndex={-1}
                    sx={{ padding: 0.5 }}
                  />
                </ListItemButton>
              );
            })}
          </>
        )}
      </List>
    </>
  );

  if (inline) {
    return (
      <Dialog
        open={open}
        slots={{ transition: Transition }}
        onClose={onClose}
        fullWidth
        maxWidth={dialogMaxWidth}
        slotProps={{
          paper: {
            elevation: 1,
            sx: {
              minHeight: 'min(700px, calc(100vh - 64px))',
              maxHeight: 'min(700px, calc(100vh - 64px))',
            },
          },
        }}
      >
        <DialogTitle
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 1,
          }}
        >
          {title}
          {headerRightSlot}
        </DialogTitle>
        <DialogContent>
          <Box data-testid={containerTestId} sx={{ marginTop: 1 }}>
            {listContent}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={onClose}>{t('Cancel')}</Button>
          <Button
            variant="contained"
            color="primary"
            onClick={onSubmit}
            disabled={submitDisabled || isLoading}
          >
            {submitLabel ?? t('Update')}
          </Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={title}
      variant={variant}
      headerActions={headerRightSlot}
    >
      <Box data-testid={containerTestId} sx={{ marginTop: 1 }}>
        {listContent}
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'flex-end',
            marginTop: 2,
          }}
        >
          <ActionButtons
            onCancel={onClose}
            onSubmit={onSubmit}
            cancelLabel={t('Cancel')}
            submitLabel={submitLabel ?? t('Update')}
            disabled={submitDisabled || isLoading}
          />
        </Box>
      </Box>
    </Drawer>
  );
};

export default SelectListPicker;
