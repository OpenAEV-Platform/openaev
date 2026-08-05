import { HelpOutlineOutlined } from '@mui/icons-material';
import {
  Box,
  Checkbox,
  Chip,
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

import { searchThreatArsenalActions } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
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
import InjectIcon from '../../../common/injects/InjectIcon';
import { formatConditionKeyLabel } from '../events/event-types';
import AddActionFooter from './AddActionFooter';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  bodyItems: { display: 'flex' },
  bodyItem: {
    fontSize: '0.875rem',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  action_kill_chain_phases: { width: '15%' },
  action_labels: { width: '35%' },
  action_domains: { width: '15%' },
  action_platforms: { width: '15%' },
  action_attack_patterns: { width: '15%' },
};

interface AddActionListProps {
  /** Bulk add of every checkbox-selected action (each will still need configuring). */
  onAddActions: (actions: ThreatArsenalAction[]) => void;
  /** Single-action selection - advances the stepper to the configure step. */
  onSelectAction: (action: ThreatArsenalAction) => void;
  /** When set, the list is pre-filtered to actions that produce this output type. */
  compatibleActionFilter?: string;
  /** Clears the compatible-output filter (surfaced as a removable chip). */
  onClearCompatibleFilter?: () => void;
}

/**
 * Embeddable action-picker body used inside the component stepper drawer. Shows the full threat
 * arsenal (no non-tabletop exclusion, so email / SMS / challenge / media actions appear). The
 * optional compatible-output filter is surfaced as a removable chip instead of a silent filter, and
 * the per-row chevron is gone: clicking a row advances to configure, the checkbox is for bulk add.
 */
const AddActionList = ({
  onAddActions,
  onSelectAction,
  compatibleActionFilter,
  onClearCompatibleFilter,
}: AddActionListProps) => {
  const { t, tPick } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();

  const [actions, setActions] = useState<ThreatArsenalAction[]>([]);
  const [loading, setLoading] = useState(false);

  const initPagination = useMemo(() => buildSearchPagination({
    size: 100,
    sorts: [{
      property: 'action_updated_at',
      direction: 'desc',
    }],
    ...(compatibleActionFilter
      ? {
          filterGroup: {
            mode: 'and' as const,
            filters: [{
              id: 'compatible-action-output-type',
              key: 'providing',
              operator: 'eq' as const,
              values: [compatibleActionFilter],
            }],
          },
        }
      : {}),
  }), [compatibleActionFilter]);

  const { queryableHelpers, searchPaginationInput } = useQueryable(initPagination);

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
    return searchThreatArsenalActions({ ...input }).finally(() => setLoading(false));
  };

  const availableFilterNames = [
    'action_injectors',
    'action_platforms',
    'action_domains',
    'action_tags',
    'providing',
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
    <Box>
      {compatibleActionFilter && (
        <Box sx={{ mb: 1.5 }}>
          <Chip
            size="small"
            color="primary"
            variant="outlined"
            label={t('Compatible output: {type}', { type: formatConditionKeyLabel(compatibleActionFilter) })}
            onDelete={onClearCompatibleFilter}
          />
        </Box>
      )}

      <PaginationComponentV2
        fetch={searchActions}
        searchPaginationInput={searchPaginationInput}
        setContent={setActions}
        entityPrefix="threat_arsenal"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
      />

      <List sx={{ pb: 8 }}>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
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
              >
                <ListItemButton onClick={() => onSelectAction(action)}>
                  <ListItemIcon style={{ minWidth: 40 }}>
                    <Checkbox
                      edge="start"
                      checked={
                        (selectAll && !(action.injector_contract_id in (deSelectedElements || {})))
                        || action.injector_contract_id in (selectedElements || {})
                      }
                      disableRipple
                      onClick={event => onToggleEntity(action, event)}
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
  );
};

export default AddActionList;
