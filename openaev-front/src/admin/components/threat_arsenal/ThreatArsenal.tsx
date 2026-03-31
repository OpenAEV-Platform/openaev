import {ForwardToInbox, HelpOutlineOutlined, MovieFilterOutlined} from '@mui/icons-material';
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
import { searchInjectorContracts } from '../../../actions/InjectorContracts';
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
  type Domain,
  type InjectorContractActionOutput,
  type SearchPaginationInput,
} from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import IconBar from '../common/domains/IconBar';
import useDomainIconFilter from '../common/domains/useDomainIconFilter';
import InjectIcon from '../common/injects/InjectIcon';
import InjectorContractPopover from '../integrations/injectors/injector_contracts/InjectorContractPopover';
import CreatePayload from '../payloads/CreatePayload';
import PayloadPopover from '../payloads/PayloadPopover';
import PayloadStatusComponent from '../payloads/PayloadStatusComponent';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ToolBar from "../common/ToolBar";
import useEntityToggle from "../../../utils/hooks/useEntityToggle";
import ThreatArsenalRunTestDrawer from "./ThreatArsenalRunTestDrawer";

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
  injector_contract_labels: { width: '30%' },
  injector_contract_domains: { width: '15%' },
  injector_contract_platforms: { width: '10%' },
  injector_contract_tags: { width: '15%' },
  injector_contract_payload_status: { width: '10%' },
  injector_contract_updated_at: { width: '20%' },
};

const ThreatArsenal = () => {
  const { t, tPick, nsdt } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();

  const [selectedInjectorContract, setSelectedInjectorContract] = useState<InjectorContractActionOutput | null>(null);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);

  const [injectorContracts, setInjectorContracts] = useState<InjectorContractActionOutput[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'threat-arsenal',
    buildSearchPagination({}),
  );

  // Headers
  const [loading, setLoading] = useState<boolean>(false);
  const searchInjectorContractsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchInjectorContracts({
      ...input,
      output_mode: 'THREAT_ARSENAL',
    }).finally(() => setLoading(false));
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
  } = useEntityToggle<InjectorContractActionOutput>('injector_contract', injectorContracts, queryableHelpers.paginationHelpers.getTotalElements());

  const headers: Header[] = useMemo(() => [
    {
      field: 'injector_contract_labels',
      label: 'Name',
      isSortable: true,
      value: (contract: InjectorContractActionOutput) => (
        <Tooltip title={tPick(contract.injector_contract_labels)}>
          <span>{tPick(contract.injector_contract_labels)}</span>
        </Tooltip>
      ),
    },
    {
      field: 'injector_contract_domains',
      label: 'Domains',
      isSortable: false,
      value: (contract: InjectorContractActionOutput) => {
        return contract.injector_contract_domains && contract.injector_contract_domains.length > 0
          ? (
              <ItemDomains
                domains={contract.injector_contract_domains}
                variant="reduced-view"
              />
            )
          : <></>;
      },
    },
    {
      field: 'injector_contract_platforms',
      label: 'Platforms',
      isSortable: false,
      value: (contract: InjectorContractActionOutput) => (
        <>
          {(contract.injector_contract_platforms ?? []).map(
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
      field: 'injector_contract_tags',
      label: 'Tags',
      isSortable: false,
      value: (contract: InjectorContractActionOutput) => (
        <ItemTags
          variant="reduced-view"
          tags={contract.injector_contract_tags}
        />
      ),
    },
    {
      field: 'injector_contract_payload_status',
      label: 'Status',
      isSortable: false,
      value: (contract: InjectorContractActionOutput) => (
        <PayloadStatusComponent status={contract.injector_contract_payload?.payload_status} />
      ),
    },
    {
      field: 'injector_contract_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (contract: InjectorContractActionOutput) => <>{nsdt(contract.injector_contract_updated_at)}</>,
    },
  ], []);

  // Sort threat arsenal by domains
  const domainOptions: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
  });

  const availableFilterNames = [
    'injector_contract_injector',
    'injector_contract_platforms',
    'injector_contract_domains',
    'injector_contract_tags',
    'injector_contract_payload_status',
    'injector_contract_updated_at',
  ];

  return (
    <Stack flexDirection="column">
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Threat Arsenal'),
          current: true,
        }]}
      />
      <IconBar elements={iconBarOrderedDomains} />

      <PaginationComponentV2
        fetch={searchInjectorContractsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setInjectorContracts}
        entityPrefix="injector_contract"
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
          : injectorContracts.map((contract, index) => {
              return (
                (
                  <ListItem
                    key={contract.injector_contract_id}
                    divider
                    secondaryAction={(contract.injector_contract_payload != null
                      ? (
                          <PayloadPopover
                            payloadId={contract.injector_contract_payload?.payload_id ?? ''}
                            name={tPick(contract.injector_contract_labels)}
                            onUpdate={(result: InjectorContractActionOutput) =>
                              setInjectorContracts(injectorContracts.map(a => a.injector_contract_id === contract.injector_contract_id ? result : a))}
                            onDuplicate={(result: InjectorContractActionOutput) => setInjectorContracts([result, ...injectorContracts])}
                            onDelete={() => setInjectorContracts(injectorContracts.filter(a => a.injector_contract_id !== contract.injector_contract_id))}
                            disableUpdate={contract.injector_contract_payload?.payload_collector_type !== null}
                            disableDelete={contract.injector_contract_payload?.payload_collector_type !== null && contract.injector_contract_payload?.payload_status !== 'DEPRECATED'}
                          />
                        )
                      : (
                          <InjectorContractPopover
                            injectorContract={contract}
                            canDelete={false}
                            canEditCustomForm={false}
                            onUpdate={(result: InjectorContractActionOutput) =>
                              setInjectorContracts(injectorContracts.map(ic => (ic.injector_contract_id !== result.injector_contract_id ? ic : result)))}
                          />
                        )
                    )}
                    disablePadding
                  >
                    <ListItemButton
                      onClick={() => setSelectedInjectorContract(contract)}
                      selected={selectedInjectorContract?.injector_contract_id === contract.injector_contract_id}
                    >
                      <ListItemIcon
                        style={{ minWidth: 40 }}
                        onClick={event => onToggleEntity(contract, event)}
                      >
                        <Checkbox
                          edge="start"
                          checked={
                            (selectAll && !(contract.injector_contract_id
                              in (deSelectedElements || {})))
                            || contract.injector_contract_id in (selectedElements || {})
                          }
                          disableRipple
                        />
                      </ListItemIcon>

                      <ListItemIcon style={{ minWidth: 56 }}>
                        <InjectIcon
                          variant="list"
                          type={
                            contract.injector_contract_payload != null
                              ? contract.injector_contract_payload.payload_collector_type ?? contract.injector_contract_payload.payload_type
                              : contract.injector_contract_injector_type
                          }
                          isPayload={contract.injector_contract_payload != null}
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
                                {header.value?.(contract)}
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
        <CreatePayload
          onCreate={(result: InjectorContractActionOutput) => {
            setInjectorContracts([result, ...injectorContracts]);
          }}
        />
      </Can>
      {(selectedInjectorContract !== null) && (
        <ThreatArsenalInformationDrawer
          open={selectedInjectorContract !== null}
          onClose={() => setSelectedInjectorContract(null)}
          injectorContract={selectedInjectorContract}
        />
      )}
      {isRunTestDrawerOpened && (
        <ThreatArsenalRunTestDrawer
          isExclusionMode={selectAll}
          isOnlyOneItemSelected={selectAll ? Object.keys(deSelectedElements).length === queryableHelpers.paginationHelpers.getTotalElements() - 1 : numberOfSelectedElements === 1}
          selectedElements={selectedElements}
          deSelectedElements={deSelectedElements}
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
            customAction={
              <Tooltip title={t('Run a test')}>
                <span>
                  <IconButton
                    aria-label="run-a-test"
                    onClick={() => setRunTestDrawerOpened(true)}
                    color="primary"
                    size="small"
                  >
                    <MovieFilterOutlined fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            }
          />
        )
      }
    </Stack>
  );
};

export default ThreatArsenal;
