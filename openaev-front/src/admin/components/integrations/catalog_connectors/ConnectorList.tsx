import { Box, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

import groupSections from '../../../../components/common/marketplace/results/groupSections';
import MarketplaceSectionHeader from '../../../../components/common/marketplace/results/MarketplaceSectionHeader';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { type Header } from '../../../../components/common/SortHeadersList';
import { type ConnectorItem, type ConnectorItemType } from './catalog-facets';
import { CollapsedUseCaseChips, ConnectorLogo, ConnectorSupportIcon } from './ConnectorParts';
import useConnectorTypeLabels from './useConnectorTypeLabels';

const INLINE_STYLES: Record<string, CSSProperties> = {
  name: { width: '25%' },
  type: { width: '10%' },
  description: { width: '25%' },
  use_cases: { width: '17%' },
  support: { width: '8%' },
  action: {
    width: '15%',
    display: 'flex',
    justifyContent: 'flex-end',
  },
};

const HEADERS: Header[] = [
  {
    field: 'name',
    label: 'Name',
    isSortable: true,
  },
  {
    field: 'type',
    label: 'Type',
    isSortable: false,
  },
  {
    field: 'description',
    label: 'Description',
    isSortable: false,
  },
  {
    field: 'use_cases',
    label: 'Use cases',
    isSortable: false,
  },
  {
    field: 'support',
    label: 'Support',
    isSortable: false,
  },
  {
    field: 'action',
    label: '',
    isSortable: false,
  },
];

interface Props {
  items: ConnectorItem[];
  sectionLabel: (type: ConnectorItemType) => string;
  sectionIcon: (type: ConnectorItemType) => ReactNode;
  renderAction?: (item: ConnectorItem) => ReactNode;
  sortHelpers: SortHelpers;
  emptyState: ReactNode;
}

// The connectors as the platform list, one section per type.
const ConnectorList: FunctionComponent<Props> = ({ items, sectionLabel, sectionIcon, renderAction, sortHelpers, emptyState }) => {
  const bodyItemsStyles = useBodyItemsStyles();
  const typeLabels = useConnectorTypeLabels();
  if (items.length === 0) {
    return <>{emptyState}</>;
  }

  const cell = (field: string, content: ReactNode, className?: string) => (
    <div
      className={className}
      style={{
        ...bodyItemsStyles.bodyItem,
        ...INLINE_STYLES[field],
      }}
    >
      {content}
    </div>
  );

  // Only the name is a link; its area stretches over the row, under the chips and the action.
  const name = (item: ConnectorItem) => (item.detailUrl
    ? (
        <Box
          component={Link}
          to={item.detailUrl}
          sx={{
            'color': 'inherit',
            'textDecoration': 'none',
            '&::after': {
              content: '""',
              position: 'absolute',
              inset: 0,
            },
          }}
        >
          {item.title}
        </Box>
      )
    : item.title);

  const raised = (field: string, content: ReactNode) => (
    <div
      style={{
        ...bodyItemsStyles.bodyItem,
        ...INLINE_STYLES[field],
        position: 'relative',
        zIndex: 1,
      }}
    >
      {content}
    </div>
  );

  const row = (item: ConnectorItem) => (
    <ListItem
      key={item.id}
      divider
      disablePadding
      data-testid="connector-line"
      sx={theme => ({
        'position': 'relative',
        'paddingInline': 2,
        'paddingBlock': 1,
        '&:hover': item.detailUrl ? { backgroundColor: theme.palette.action.hover } : {},
      })}
    >
      <ListItemIcon sx={theme => ({
        '& img, & svg': {
          width: theme.spacing(3),
          height: theme.spacing(3),
          objectFit: 'contain',
        },
      })}
      >
        <ConnectorLogo connector={item} />
      </ListItemIcon>
      <ListItemText
        primary={(
          <div style={bodyItemsStyles.bodyItems}>
            {cell('name', name(item))}
            {cell('type', typeLabels[item.type], 'text-filigran-brand-primary')}
            {cell('description', item.description || '-', 'text-default-secondary')}
            {raised('use_cases', <CollapsedUseCaseChips useCases={item.useCases} />)}
            {raised('support', <ConnectorSupportIcon connector={item} />)}
            {raised('action', renderAction?.(item))}
          </div>
        )}
      />
    </ListItem>
  );

  return (
    <List disablePadding>
      <ListItem>
        <ListItemIcon />
        <ListItemText
          primary={(
            <SortHeadersComponentV2
              headers={HEADERS}
              inlineStylesHeaders={INLINE_STYLES}
              sortHelpers={sortHelpers}
            />
          )}
        />
      </ListItem>
      {groupSections(items, item => item.type).map(section => (
        <Box key={section.key ?? 'other'}>
          <Box sx={{ paddingBlock: 2 }}>
            <MarketplaceSectionHeader
              icon={sectionIcon(section.key as ConnectorItemType)}
              label={sectionLabel(section.key as ConnectorItemType)}
              count={section.items.length}
            />
          </Box>
          {section.items.map(row)}
        </Box>
      ))}
    </List>
  );
};

export default ConnectorList;
