import { ChevronRight, HelpOutlineOutlined } from '@mui/icons-material';
import {
  Box,
  Checkbox,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Tooltip,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchNonTabletopThreatArsenalActions } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import Drawer from '../../../../../components/common/Drawer';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryable } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../../components/i18n';
import ItemDomains from '../../../../../components/ItemDomains';
import PaginatedListLoader from '../../../../../components/PaginatedListLoader';
import PlatformIcon from '../../../../../components/PlatformIcon';
import type { SearchPaginationInput, ThreatArsenalAction } from '../../../../../utils/api-types';
import useEntityToggle from '../../../../../utils/hooks/useEntityToggle';
import DrawerBreadcrumb from '../../../common/DrawerBreadcrumb';
import InjectIcon from '../../../common/injects/InjectIcon';
import AddActionFooter from './AddActionFooter';

const useStyles = makeStyles()(theme => ({
  itemHead: { textTransform: 'uppercase' },
  bodyItems: { display: 'flex' },
  bodyItem: {
    fontSize: theme.typography.body2.fontSize,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  listItem: {
    '& .chevron-action': { visibility: 'hidden' },
    '&:hover .chevron-action': { visibility: 'visible' },
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  action_kill_chain_phases: { width: '15%' },
  action_labels: { width: '30%' },
  action_domains: { width: '15%' },
  action_platforms: { width: '15%' },
  action_attack_patterns: { width: '15%' },
};

interface AddActionListProps {
  open: boolean;
  onClose: () => void;
  onBack: () => void;
  onAddActions: (actions: ThreatArsenalAction[]) => void;
  onSelectAction?: (action: ThreatArsenalAction) => void;
}

const AddActionList = ({ open, onClose, onBack, onAddActions, onSelectAction }: AddActionListProps) => {
  const { t, tPick } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();

  const [actions, setActions] = useState<ThreatArsenalAction[]>([]);
  const [loading, setLoading] = useState(false);

  const { queryableHelpers, searchPaginationInput } = useQueryable(
    buildSearchPagination({
      size: 100,
      sorts: [{
        property: 'action_updated_at',
        direction: 'desc',
      }],
    }),
  );

  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<ThreatArsenalAction>('injector_contract', actions, queryableHelpers.paginationHelpers.getTotalElements());

  const searchActions = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchNonTabletopThreatArsenalActions({ ...input }).finally(() => setLoading(false));
  };

  const availableFilterNames = [
    'action_injectors',
    'action_platforms',
    'action_domains',
    'action_tags',
  ];

  const headers: Header[] = useMemo(() => [
    {
      field: 'action_labels',
      label: 'Name',
      isSortable: true,
      value: (action: ThreatArsenalAction) => (
        <Tooltip title={tPick(action.action_labels)}>
          <span>{tPick(action.action_labels)}</span>
        </Tooltip>
      ),
    },
    {
      field: 'action_domains',
      label: 'Domains',
      isSortable: false,
      value: (action: ThreatArsenalAction) => {
        return action.action_domains_ids && action.action_domains_ids.length > 0
          ? <ItemDomains domains={action.action_domains_ids} variant="reduced-view" />
          : <>-</>;
      },
    },
    {
      field: 'action_platforms',
      label: 'Platforms',
      isSortable: false,
      value: (action: ThreatArsenalAction) => (
        <>
          {(action.action_platforms ?? []).map(
            (platform: string) => (
              <PlatformIcon
                key={platform}
                width={20}
                platform={platform}
                marginRight={theme.spacing(2)}
              />
            ),
          )}
        </>
      ),
    },
    {
      field: 'action_attack_patterns',
      label: 'Attack patterns',
      isSortable: false,
      value: (action: ThreatArsenalAction) => {
        const externalId = action.injector_contract_external_id;
        return <>{externalId || '-'}</>;
      },
    },
  ], []);

  const handleAddActions = () => {
    const selected: ThreatArsenalAction[] = selectAll
      ? actions.filter(a => !(a.injector_contract_id in (deSelectedElements || {})))
      : Object.values(selectedElements);
    onAddActions(selected);
    handleClearSelectedElements();
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Add actions')}
    >
      <Box>
        <DrawerBreadcrumb
          parentLabel={t('Add component')}
          currentLabel={t('Add actions')}
          onBack={onBack}
        />

        <PaginationComponentV2
          fetch={searchActions}
          searchPaginationInput={searchPaginationInput}
          setContent={setActions}
          entityPrefix="threat_arsenal"
          availableFilterNames={availableFilterNames}
          queryableHelpers={queryableHelpers}
        />

        <List sx={{ pb: 6 }}>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
              />
            </ListItemIcon>
            <ListItemIcon />
            <ListItemText
              primary={(
                <SortHeadersComponentV2
                  headers={headers}
                  inlineStylesHeaders={inlineStyles}
                  sortHelpers={queryableHelpers.sortHelpers}
                />
              )}
            />
          </ListItem>
          {loading
            ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox />
            : actions.map(action => (
                <ListItem
                  key={action.injector_contract_id}
                  divider
                  disablePadding
                  className={classes.listItem}
                  secondaryAction={(
                    <IconButton
                      edge="end"
                      size="small"
                      className="chevron-action"
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelectAction?.(action);
                      }}
                    >
                      <ChevronRight />
                    </IconButton>
                  )}
                >
                  <ListItemButton onClick={event => onToggleEntity(action, event)}>
                    <ListItemIcon style={{ minWidth: 40 }}>
                      <Checkbox
                        edge="start"
                        checked={
                          (selectAll && !(action.injector_contract_id in (deSelectedElements || {})))
                          || action.injector_contract_id in (selectedElements || {})
                        }
                        disableRipple
                      />
                    </ListItemIcon>
                    <ListItemIcon style={{ minWidth: 56 }}>
                      <InjectIcon
                        type={
                          action.action_payload != null
                            ? action.action_payload.payload_collector_type ?? action.action_payload.payload_type
                            : action.action_injector_type
                        }
                        isPayload={action.action_payload != null}
                        variant="list"
                      />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div className={classes.bodyItems}>
                          {headers.map(header => (
                            <div
                              key={header.field}
                              className={classes.bodyItem}
                              style={inlineStyles[header.field]}
                            >
                              {header.value?.(action)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
        </List>

        <AddActionFooter
          numberOfSelectedElements={numberOfSelectedElements}
          onClear={handleClearSelectedElements}
          onSubmit={handleAddActions}
        />
      </Box>
    </Drawer>
  );
};

export default AddActionList;
