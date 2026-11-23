import { Skeleton, SkeletonGroup } from '@filigran/design-system';
// fds:keep-mui the library has no list: rows follow the platform lists (ConnectorList, ThreatArsenalList)
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, Fragment, type ReactNode } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../i18n';
import SortHeadersComponentV2 from '../../queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../queryable/sort/SortHelpers';
import useBodyItemsStyles from '../../queryable/style/style';
import groupSections from './groupSections';
import MarketplaceSectionHeader from './MarketplaceSectionHeader';
import { type Sections } from './MarketplaceSections';

export interface MarketplaceColumn<T> {
  // The sort key of the search when the column sorts.
  field: string;
  // Translated by the header.
  label: string;
  sortable?: boolean;
  width: string;
  value: (item: T) => ReactNode;
}

// Opens the item in place, or leads to its page.
export type MarketplaceRowTarget = { onClick: () => void } | { to: string };

interface Props<T> {
  items: T[];
  columns: MarketplaceColumn<T>[];
  sections?: Sections<T>;
  sortHelpers: SortHelpers;
  rowKey: (item: T) => string;
  icon: (item: T) => ReactNode;
  open: (item: T) => MarketplaceRowTarget;
  action?: (item: T) => ReactNode;
  emptyState: ReactNode;
  loading: boolean;
}

// The list view of a marketplace: one row per item, the same sections as the grid.
const MarketplaceList = <T, >({ items, columns, sections, sortHelpers, rowKey, icon, open, action, emptyState, loading }: Props<T>) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const bodyItemsStyles = useBodyItemsStyles();
  if (!loading && items.length === 0) {
    return <>{emptyState}</>;
  }
  const widths: Record<string, CSSProperties> = Object.fromEntries(columns.map(column => [column.field, { width: column.width }]));
  const cells = (item: T) => (
    <div style={bodyItemsStyles.bodyItems}>
      {columns.map(column => (
        <div
          key={column.field}
          style={{
            ...bodyItemsStyles.bodyItem,
            ...widths[column.field],
          }}
        >
          {column.value(item)}
        </div>
      ))}
    </div>
  );
  const row = (item: T) => {
    const target = open(item);
    const content = (
      <>
        <ListItemIcon>{icon(item)}</ListItemIcon>
        <ListItemText primary={cells(item)} />
      </>
    );
    return (
      <ListItem key={rowKey(item)} divider disablePadding secondaryAction={action?.(item)}>
        {'to' in target
          ? (
              <ListItemButton component={Link} to={target.to}>{content}</ListItemButton>
            )
          : (
              <ListItemButton onClick={target.onClick}>{content}</ListItemButton>
            )}
      </ListItem>
    );
  };
  const everything = [{
    key: undefined,
    items,
  }];
  const groups = sections ? groupSections(items, sections.sectionKey) : everything;

  return (
    <List disablePadding>
      <ListItem secondaryAction={action && <>&nbsp;</>}>
        <ListItemIcon />
        <ListItemText
          primary={(
            <SortHeadersComponentV2
              headers={columns.map(column => ({
                field: column.field,
                label: column.label,
                isSortable: !!column.sortable,
              }))}
              inlineStylesHeaders={widths}
              sortHelpers={sortHelpers}
            />
          )}
        />
      </ListItem>
      {loading
        ? (
            <SkeletonGroup loading label={t('Loading')}>
              {Array.from({ length: 10 }).map((_, idx) => (
                <ListItem key={idx} divider>
                  <ListItemText primary={<Skeleton />} />
                </ListItem>
              ))}
            </SkeletonGroup>
          )
        : groups.map(group => (
            <Fragment key={group.key ?? 'other'}>
              {sections && (
                <div style={{ paddingBlock: theme.spacing(2) }}>
                  <MarketplaceSectionHeader icon={sections.sectionIcon(group.key)} label={sections.sectionLabel(group.key)} count={group.items.length} />
                </div>
              )}
              {group.items.map(row)}
            </Fragment>
          ))}
    </List>
  );
};

export default MarketplaceList;
