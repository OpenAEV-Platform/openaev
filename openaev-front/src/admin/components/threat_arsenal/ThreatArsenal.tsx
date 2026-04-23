import { HelpOutlineOutlined, MovieFilterOutlined } from '@mui/icons-material';
import {
  Checkbox, IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Tooltip,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import { searchThreatArsenalActions } from '../../../actions/threat_arsenals/ThreatArsenal-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import ItemDomains from '../../../components/ItemDomains';
import ItemTags from '../../../components/ItemTags';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import {
  type Domain, type InjectorContract,
  type SearchPaginationInput,
  type ThreatArsenalAction,
} from '../../../utils/api-types';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import IconBar from '../common/domains/IconBar';
import useDomainIconFilter from '../common/domains/useDomainIconFilter';
import InjectIcon from '../common/injects/InjectIcon';
import ToolBar from '../common/ToolBar';
import InjectorContractPopover from '../integrations/injectors/injector_contracts/InjectorContractPopover';
import PayloadStatusComponent from '../payloads/PayloadStatusComponent';
import ThreatArsenalRunTestDrawer from './bulk/ThreatArsenalRunTestDrawer';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import ThreatArsenalActionPopover from './ThreatArsenalActionPopover';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';

const useStyles = makeStyles()(theme => ({
  itemHead: { textTransform: 'uppercase' },
  bodyItems: { display: 'flex' },
  bodyItem: {
    fontSize: theme.typography.body2.fontSize,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  action_labels: { width: '30%' },
  action_domains: { width: '15%' },
  action_platforms: { width: '10%' },
  action_tags: { width: '15%' },
  action_payload_status: { width: '10%' },
  action_updated_at: { width: '20%' },
};

const ThreatArsenal = () => {
  const { t, tPick, nsdt } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();

  const [selectedThreatArsenalAction, setSelectedThreatArsenalAction] = useState<ThreatArsenalAction | null>(null);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);
  const [threatArsenalActions, setThreatArsenalActions] = useState<ThreatArsenalAction[]>([]);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'threat-arsenal',
    buildSearchPagination({}),
  );

  // Headers
  const [loading, setLoading] = useState<boolean>(false);
  const searchThreatArsenalActionsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchThreatArsenalActions({ ...input }).finally(() => setLoading(false));
  };

  // Toolbar
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<ThreatArsenalAction>('injector_contract', threatArsenalActions, queryableHelpers.paginationHelpers.getTotalElements());

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
          ? (
              <ItemDomains
                domains={action.action_domains_ids}
                variant="reduced-view"
              />
            )
          : <></>;
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
      field: 'action_tags',
      label: 'Tags',
      isSortable: false,
      value: (action: ThreatArsenalAction) => (
        <ItemTags
          variant="reduced-view"
          tags={action.action_tags_ids}
        />
      ),
    },
    {
      field: 'action_payload_status',
      label: 'Status',
      isSortable: false,
      value: (action: ThreatArsenalAction) => (
        <PayloadStatusComponent status={action.action_payload?.payload_status} />
      ),
    },
    {
      field: 'action_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (action: ThreatArsenalAction) => <>{nsdt(action.injector_contract_updated_at)}</>,
    },
  ], []);

  // Sort threat arsenal by domains
  const domainOptions: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
    apiPrefix: 'threat_arsenals',
    domainFilterKey: 'action_domains',
  });

  const availableFilterNames = [
    'action_injectors',
    'action_platforms',
    'action_domains',
    'action_tags',
    'action_payload_status',
    'action_updated_at',
  ];

  return (
    <Stack flexDirection="column">
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Threat Arsenals'),
          current: true,
        }]}
      />
      <IconBar elements={iconBarOrderedDomains} />

      <PaginationComponentV2
        fetch={searchThreatArsenalActionsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setThreatArsenalActions}
        entityPrefix="threat_arsenal"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
      />
      <List>
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
              disabled={typeof handleToggleSelectAll !== 'function'}
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
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : threatArsenalActions.map((action) => {
              return (
                (
                  <ListItem
                    key={action.injector_contract_id}
                    divider
                    secondaryAction={(action.action_payload != null
                      ? (
                          <ThreatArsenalActionPopover
                            actionId={action.injector_contract_id}
                            payloadId={action.action_payload.payload_id ?? ''}
                            name={tPick(action.action_labels)}
                            onUpdate={(result: ThreatArsenalAction) =>
                              setThreatArsenalActions(threatArsenalActions.map(a => a.injector_contract_id === action.injector_contract_id ? result : a))}
                            onDuplicate={(result: ThreatArsenalAction) => setThreatArsenalActions([result, ...threatArsenalActions])}
                            onDelete={() => setThreatArsenalActions(threatArsenalActions.filter(a => a.injector_contract_id !== action.injector_contract_id))}
                            disableUpdate={action.action_payload?.payload_collector_type !== null}
                            disableDelete={action.action_payload?.payload_collector_type !== null && action.action_payload?.payload_status !== 'DEPRECATED'}
                          />
                        )
                      : (
                          <InjectorContractPopover
                            injectorContract={{
                              injector_contract_id: action.injector_contract_id,
                              injector_contract_attack_patterns: action.action_attack_patterns_ids,
                              injector_contract_domains: action.action_domains_ids,
                              injector_contract_tags: action.action_tags_ids,
                            }}
                            canDelete={false}
                            canEditCustomForm={false}
                            onUpdate={(result: InjectorContract) =>
                              setThreatArsenalActions(threatArsenalActions.map(ic => (ic.injector_contract_id !== result.injector_contract_id
                                ? ic
                                : {
                                    ...ic,
                                    action_attack_patterns_ids: result.injector_contract_attack_patterns ?? [],
                                    action_domains_ids: result.injector_contract_domains ?? [],
                                    action_tags_ids: result.injector_contract_tags ?? [],
                                  })))}
                          />
                        )
                    )}
                    disablePadding
                  >
                    <ListItemButton
                      onClick={() => setSelectedThreatArsenalAction(action)}
                      selected={selectedThreatArsenalAction?.injector_contract_id === action.injector_contract_id}
                    >
                      <ListItemIcon
                        style={{ minWidth: 40 }}
                        onClick={event => onToggleEntity(action, event)}
                      >
                        <Checkbox
                          edge="start"
                          checked={
                            (selectAll && !(action.injector_contract_id
                              in (deSelectedElements || {})))
                              || action.injector_contract_id in (selectedElements || {})
                          }
                          disableRipple
                        />
                      </ListItemIcon>

                      <ListItemIcon style={{ minWidth: 56 }}>
                        <InjectIcon
                          variant="list"
                          type={
                            action.action_payload != null
                              ? action.action_payload.payload_collector_type ?? action.action_payload.payload_type
                              : action.action_injector_type
                          }
                          isPayload={action.action_payload != null}
                        />
                      </ListItemIcon>

                      <ListItemText
                        primary={(
                          <div className={classes.bodyItems}>
                            {headers.map(header => (
                              <div
                                key={header.field}
                                style={{ ...inlineStyles[header.field] }}
                              >
                                {header.value?.(action)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                )
              );
            })}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.PAYLOADS}>
        <CreateThreatArsenalAction
          onCreate={(result: ThreatArsenalAction) => {
            setThreatArsenalActions([result, ...threatArsenalActions]);
          }}
        />
      </Can>
      {(selectedThreatArsenalAction !== null) && (
        <ThreatArsenalInformationDrawer
          open={true}
          onClose={() => setSelectedThreatArsenalAction(null)}
          threatArsenalAction={selectedThreatArsenalAction}
        />
      )}
      {isRunTestDrawerOpened && (
        <ThreatArsenalRunTestDrawer
          isExclusionMode={selectAll}
          isOnlyOneItemSelected={selectAll ? Object.keys(deSelectedElements).length === queryableHelpers.paginationHelpers.getTotalElements() - 1 : numberOfSelectedElements === 1}
          selectedElements={selectedElements}
          deSelectedElements={deSelectedElements}
          searchPaginationInput={searchPaginationInput}
          open={isRunTestDrawerOpened}
          onClose={() => setRunTestDrawerOpened(false)}
        />
      )}
      {
        numberOfSelectedElements > 0 && (
          <ToolBar
            numberOfSelectedElements={numberOfSelectedElements}
            totalNumberOfElements={queryableHelpers.paginationHelpers.getTotalElements()}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            selectAll={selectAll}
            handleClearSelectedElements={handleClearSelectedElements}
            teamsFromExerciseOrScenario={[]}
            customAction={(
              <Tooltip title={t('Run a test')}>
                <IconButton
                  aria-label="run-a-test"
                  onClick={() => setRunTestDrawerOpened(true)}
                  color="primary"
                  size="small"
                >
                  <MovieFilterOutlined fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          />
        )
      }
    </Stack>
  );
};

export default ThreatArsenal;
