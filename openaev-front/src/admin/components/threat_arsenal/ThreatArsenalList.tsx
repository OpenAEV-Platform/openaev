import { Box, List, ListItem, ListItemIcon, ListItemText, Skeleton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../components/common/queryable/sort/SortHelpers';
import computeActionFlags from './threatArsenalActionFlags';
import { type ThreatArsenalItemsProps } from './ThreatArsenalCardGrid';
import { THREAT_ARSENAL_LIST_HEADERS, THREAT_ARSENAL_LIST_INLINE_STYLES } from './threatArsenalListConfig';
import ThreatArsenalListRow from './ThreatArsenalListRow';

interface Props extends ThreatArsenalItemsProps { sortHelpers: SortHelpers }

const ThreatArsenalList: FunctionComponent<Props> = ({
  actions,
  loading,
  emptyState,
  sortHelpers,
  isChecked,
  onSelect,
  onToggle,
  onUpdate,
  onDuplicate,
  onDelete,
}) => {
  const theme = useTheme();
  if (!loading && actions.length === 0) {
    return <>{emptyState}</>;
  }
  return (
    <List disablePadding>
      <ListItem
        divider
        secondaryAction={<>&nbsp;</>}
        sx={{ paddingLeft: 2 }}
      >
        {/* Spacers align the header labels with the checkbox + icon columns. */}
        <ListItemIcon style={{ minWidth: 38 }} />
        <ListItemIcon style={{ minWidth: 40 }} />
        <ListItemText
          primary={(
            <SortHeadersComponentV2
              headers={THREAT_ARSENAL_LIST_HEADERS}
              inlineStylesHeaders={THREAT_ARSENAL_LIST_INLINE_STYLES}
              sortHelpers={sortHelpers}
            />
          )}
        />
      </ListItem>
      {/* Skeleton rows mirror the exact real row anatomy (checkbox column,
          inject icon column, then the shared column widths) so the layout
          does not shift when the data lands. */}
      {loading && Array.from({ length: 10 }).map((_, idx) => (
        <Box
          key={idx}
          sx={{
            height: 50,
            display: 'flex',
            alignItems: 'center',
            paddingLeft: 2,
            paddingRight: 7,
            borderBottom: `1px solid ${theme.palette.divider}`,
          }}
        >
          <Box sx={{ minWidth: 38 }}>
            <Skeleton variant="rounded" width={18} height={18} animation="wave" />
          </Box>
          <Box sx={{ minWidth: 40 }}>
            <Skeleton variant="circular" width={26} height={26} animation="wave" />
          </Box>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            flex: 1,
          }}
          >
            {THREAT_ARSENAL_LIST_HEADERS.map(header => (
              <Box
                key={header.field}
                style={THREAT_ARSENAL_LIST_INLINE_STYLES[header.field]}
                sx={{ paddingRight: '10px' }}
              >
                <Skeleton variant="text" width="70%" height={20} animation="wave" />
              </Box>
            ))}
          </Box>
        </Box>
      ))}
      {!loading && actions.map(action => (
        <ThreatArsenalListRow
          key={action.injector_contract_id}
          action={action}
          checked={isChecked(action)}
          onSelect={() => onSelect(action)}
          onToggleEntity={event => onToggle(action, event)}
          onUpdate={onUpdate}
          onDuplicate={onDuplicate}
          onDelete={() => onDelete(action.injector_contract_id)}
          {...computeActionFlags(action)}
        />
      ))}
    </List>
  );
};

export default ThreatArsenalList;
