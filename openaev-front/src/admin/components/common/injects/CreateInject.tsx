import { Add, HelpOutlined, HighlightOffOutlined, KeyboardArrowRight } from '@mui/icons-material';
import { Avatar, Checkbox, Chip, IconButton, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Slide, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type AxiosResponse } from 'axios';
import {
  type CSSProperties,
  type FunctionComponent,
  type SyntheticEvent,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import type { DomainHelper } from '../../../../actions/helper';
import { fetchDomainCounts, searchInjectorContracts } from '../../../../actions/InjectorContracts';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import { type InjectOutputType, type InjectStore } from '../../../../actions/injects/Inject';
import { type KillChainPhaseHelper } from '../../../../actions/kill_chain_phases/killchainphase-helper';
import Drawer from '../../../../components/common/Drawer';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import ItemDomains from '../../../../components/ItemDomains';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useHelper } from '../../../../store';
import {
  type Article,
  type AtomicTestingInput,
  type AttackPattern, type Domain,
  type FilterGroup,
  type InjectInput,
  type InjectorContract, type InjectorContractDomainCountOutput,
  type InjectorContractFullOutput,
  type KillChainPhase,
  type Variable,
} from '../../../../utils/api-types';
import { type InjectorContractConverted } from '../../../../utils/api-types-custom';
import { type Error as APIError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import computeAttackPatterns from '../../../../utils/injector_contract/InjectorContractUtils';
import { isNotEmptyField } from '../../../../utils/utils';
import { buildOrderedDomains } from '../../workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';
import { InjectContext } from '../Context';
import buildIconBarElements from '../domains/DomainsIcons';
import IconBar from '../domains/IconBar';
import BulkToolBar from '../toolBar/BulkToolBar';
import { type ToolTasks } from '../toolBar/BulkToolBar-model';
import InjectForm from './form/InjectForm';
import InjectCardComponent from './InjectCardComponent';
import InjectIcon from './InjectIcon';

const useStyles = makeStyles()(theme => ({
  itemHead: { textTransform: 'uppercase' },
  bodyItems: { display: 'flex' },
  bodyItem: {
    fontSize: theme.typography.body2.fontSize,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  chipInList: {
    fontSize: theme.typography.caption.fontSize,
    height: 20,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 80,
    marginRight: 5,
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  kill_chain_phase: { width: '15%' },
  injector_contract_labels: { width: '40%' },
  injector_contract_domains: { width: '15%' },
  injector_contract_platforms: { width: '10%' },
  attack_patterns: { width: '20%' },
};

interface Props {
  title: string;
  onCreateInject: (data: InjectInput | AtomicTestingInput) => Promise<void>;
  onCreateInjects?: (data: InjectOutputType[]) => void;
  isAtomic?: boolean;
  open?: boolean;
  handleClose: () => void;
  presetInjectDuration?: number;
  articlesFromExerciseOrScenario?: Article[];
  uriVariable?: string;
  variablesFromExerciseOrScenario?: Variable[];
}

const CreateInject: FunctionComponent<Props> = ({
  title,
  onCreateInject,
  onCreateInjects,
  open = false,
  handleClose,
  isAtomic = false,
  presetInjectDuration = 0,
  articlesFromExerciseOrScenario = [],
  uriVariable = '',
  variablesFromExerciseOrScenario = [],
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t, tPick } = useFormatter();
  const injectContext = useContext(InjectContext);

  // Fetching data
  const {
    attackPatterns,
    attackPatternsMap,
    killChainPhasesMap,
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper & InjectorHelper) => ({
    attackPatterns: helper.getAttackPatterns(),
    attackPatternsMap: helper.getAttackPatternsMap(),
    killChainPhasesMap: helper.getKillChainPhasesMap(),
  }));

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'kill_chain_phase',
      label: 'Kill chain phase',
      isSortable: false,
      value: (_: InjectorContractFullOutput, killChainPhase: KillChainPhase, __: Record<string, AttackPattern>) => {
        return (killChainPhase ? killChainPhase.phase_name : '-');
      },
    },
    {
      field: 'injector_contract_labels',
      label: 'Name',
      isSortable: false,
      value: (contract: InjectorContractFullOutput, _: KillChainPhase, __: Record<string, AttackPattern>) => (
        <Tooltip title={tPick(contract.injector_contract_labels)}>
          <span>{tPick(contract.injector_contract_labels)}</span>
        </Tooltip>
      ),
    },
    {
      field: 'injector_contract_domains',
      label: 'Payload domains',
      isSortable: false,
      value: (contract: InjectorContractFullOutput, _: KillChainPhase, __: Record<string, AttackPattern>) => {
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
      value: (contract: InjectorContractFullOutput, _: KillChainPhase, __: Record<string, AttackPattern>) => (
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
      field: 'attack_patterns',
      label: 'Attack patterns',
      isSortable: false,
      value: (contract: InjectorContractFullOutput, _: KillChainPhase, contractAttackPatterns: Record<string, AttackPattern>) => (
        <>
          {Object.values(contractAttackPatterns)
            .filter((value, index, self) => index === self.findIndex(v => v.attack_pattern_external_id === value.attack_pattern_external_id))
            .map((contractAttackPattern: AttackPattern) => (
              <Chip
                key={`${contract.injector_contract_id}-${contractAttackPattern.attack_pattern_id}-${Math.random()}`}
                variant="outlined"
                classes={{ root: classes.chipInList }}
                color="primary"
                label={contractAttackPattern.attack_pattern_external_id}
              />
            ))}
        </>
      ),
    },
  ], []);

  // Filters
  const addAtomicFilter = () => {
    const filterGroup: FilterGroup = {
      mode: 'and',
      filters: [],
    };
    if (filterGroup.filters?.map(f => f.key).includes('injector_contract_atomic_testing')) {
      return filterGroup;
    }

    filterGroup.filters?.push({
      key: 'injector_contract_atomic_testing',
      operator: 'eq',
      values: ['true'],
    });

    return filterGroup;
  };

  const availableFilterNames = [
    'injector_contract_attack_patterns',
    'injector_contract_injector',
    'injector_contract_kill_chain_phases',
    'injector_contract_labels',
    'injector_contract_platforms',
    'injector_contract_players',
    'injector_contract_arch',
    'injector_contract_domains',
  ];

  // Contracts
  const [contracts, setContracts] = useState<InjectorContractFullOutput[]>([]);
  const initSearchPaginationInput = () => {
    return ({
      sorts: initSorting('injector_contract_labels'),
      filterGroup: isAtomic ? addAtomicFilter() : {} as FilterGroup,
      size: 100,
      page: 0,
    });
  };

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(isAtomic ? 'injector-contracts-atomic' : 'injector-contracts', initSearchPaginationInput());

  // Toolbar
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<InjectorContractFullOutput>('injector_contract', contracts, queryableHelpers.paginationHelpers.getTotalElements());
  const onRowShiftClick = (currentIndex: number, currentEntity: { injector_contract_id: string }, event: SyntheticEvent | null = null) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    if (selectedElements && Object.entries(selectedElements).length > 0) {
      // Find the indexes of the first and last selected entities
      const values = Object.values(selectedElements);
      let firstIndex = contracts.findIndex(c => c.injector_contract_id === values[0].injector_contract_id);
      if (currentIndex > firstIndex) {
        let entities: InjectorContractFullOutput[] = [];
        while (firstIndex <= currentIndex) {
          entities = [...entities, contracts[firstIndex]];

          firstIndex++;
        }
        const forcedRemove = values.filter(
          (n: InjectorContractFullOutput) => !entities.map(o => o.injector_contract_id).includes(n.injector_contract_id),
        );
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-expect-error
        return onToggleEntity(entities, event, forcedRemove);
      }
      let entities: InjectorContractFullOutput[] = [];
      while (firstIndex >= currentIndex) {
        entities = [...entities, contracts[firstIndex]];

        firstIndex--;
      }
      const forcedRemove = values.filter(
        (n: InjectorContractFullOutput) => !entities.map(o => o.injector_contract_id).includes(n.injector_contract_id),
      );
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-expect-error
      return onToggleEntity(entities, event, forcedRemove);
    }
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-expect-error
    return onToggleEntity(currentEntity, event);
  };

  // Slider
  const [checked, setChecked] = useState<boolean>(false);
  const handleSlide = () => {
    setChecked(true);
  };

  const [selectedContract, setSelectedContract] = useState<Omit<InjectorContractFullOutput, 'injector_contract_content'> & { injector_contract_content: InjectorContractConverted['convertedContent'] } | null>(null);
  const selectContract = (contract: InjectorContractFullOutput) => {
    const parsedContract: Omit<InjectorContractFullOutput, 'injector_contract_content'> & { injector_contract_content: InjectorContractConverted['convertedContent'] } = {
      ...contract,
      injector_contract_content: JSON.parse(contract.injector_contract_content),
    };
    setSelectedContract(parsedContract);
    handleSlide();
  };

  const handleCloseDrawer = () => {
    setSelectedContract(null);
    handleClose();
  };

  const handleToggle = (contract: InjectorContractFullOutput, event: SyntheticEvent) => {
    onToggleEntity(contract, event);
    setSelectedContract(null);
  };

  const onCreateMultipleInjectsInject = async (data: InjectInput[]) => {
    await injectContext.onAddMultipleInjects(data).then(
      (result: {
        result: string[];
        entities: { injects: Record<string, InjectStore> };
      }) => {
        if (onCreateInjects && result.entities) {
          const created: InjectOutputType[] = [];
          result.result.map((r: string) => {
            created.push(result.entities.injects[r]);
          });
          onCreateInjects(created);
        }
        handleCloseDrawer();
      });
  };

  const buildQuickInject = (elements: Record<string, InjectorContractFullOutput>) => {
    const quickInjects: InjectInput[] = [];
    for (const [_k, v] of Object.entries(elements)) {
      const quickInject: InjectInput = {
        inject_title: tPick(v.injector_contract_labels),
        inject_injector_contract: v.injector_contract_id,
        inject_depends_duration: presetInjectDuration,
      };
      quickInjects.push(quickInject);
    }
    onCreateMultipleInjectsInject(quickInjects);
  };

  const toolTasks: ToolTasks[] = [
    {
      type: 'info',
      icon: () => (<Add />),
      function: () => buildQuickInject(selectedElements),
    },
  ];

  let selectedContractKillChainPhase = null;
  if (selectedContract) {
    const selectedContractAttackPatterns = computeAttackPatterns(selectedContract?.injector_contract_attack_patterns, attackPatternsMap);
    const killChainPhaseForSelection = selectedContractAttackPatterns
      .flatMap((contractAttackPattern: AttackPattern) => contractAttackPattern.attack_pattern_kill_chain_phases ?? [])
      .at(0);
    selectedContractKillChainPhase = killChainPhaseForSelection && killChainPhasesMap[killChainPhaseForSelection]
      ? `${killChainPhasesMap[killChainPhaseForSelection].phase_name} / ${selectedContractAttackPatterns.map((attackPattern: AttackPattern) => attackPattern.attack_pattern_external_id).join(', ')}`
      : null;
  }
  const domainOptions: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());

  // Domains
  const [selectedDomains, setSelectedDomains] = useState<string[]>([]);
  const [domainCounts, setDomainCounts] = useState<Record<string, number>>({});
  const DOMAIN_FILTER_KEY = 'injector_contract_domains';

  const handleDomainClick = (domainId: string) => {
    if (!queryableHelpers?.filterHelpers) return;

    const isAlreadySelected = selectedDomains.includes(domainId);
    const updated = isAlreadySelected
      ? selectedDomains.filter(id => id !== domainId)
      : [...selectedDomains, domainId];

    if (updated.length === 0) {
      queryableHelpers.filterHelpers.handleRemoveFilterByKey(DOMAIN_FILTER_KEY);
    } else {
      const domainFilterExists = searchPaginationInput?.filterGroup?.filters?.some(
        f => f.key === DOMAIN_FILTER_KEY,
      );

      if (!domainFilterExists) {
        queryableHelpers.filterHelpers.handleAddFilterWithEmptyValue({
          key: DOMAIN_FILTER_KEY,
          operator: 'contains',
          values: updated,
          mode: 'or',
        });
      } else {
        queryableHelpers.filterHelpers.handleAddMultipleValueFilter(
          DOMAIN_FILTER_KEY,
          updated,
        );
      }
    }
  };

  // Fetch and update domain counts whenever search filters change
  useEffect(() => {
    if (searchPaginationInput) {
      fetchDomainCounts(searchPaginationInput)
        .then((response: AxiosResponse<InjectorContractDomainCountOutput[]>) => {
          const data = response?.data;

          if (Array.isArray(data)) {
            const countsMap = data.reduce((acc, curr) => {
              if (curr.domain) {
                acc[curr.domain] = curr.count ?? 0;
              }
              return acc;
            }, {} as Record<string, number>);

            setDomainCounts(countsMap);
          } else {
            notifyErrorHandler({
              status: 400,
              message: 'Invalid data format received',
            });
          }
        })
        .catch((error: unknown) => {
          notifyErrorHandler(error as APIError);
        });
    }
  }, [searchPaginationInput]);

  // Sync icon bar selection with the global filter group
  useEffect(() => {
    const domainFilter = searchPaginationInput?.filterGroup?.filters?.find(
      f => f.key === DOMAIN_FILTER_KEY,
    );

    if (domainFilter && Array.isArray(domainFilter.values)) {
      setSelectedDomains(domainFilter.values as string[]);
    } else {
      setSelectedDomains([]);
    }
  }, [searchPaginationInput?.filterGroup]);

  const iconBarElements = useMemo(
    () => buildIconBarElements(domainOptions, handleDomainClick, selectedDomains, domainCounts),
    [domainOptions, selectedDomains, domainCounts],
  );

  const iconBarOrderedDomains = useMemo(() => buildOrderedDomains(iconBarElements), [iconBarElements]);

  return (
    <Drawer
      open={open}
      handleClose={handleCloseDrawer}
      title={title}
      variant="full"
      disableEnforceFocus
    >
      <>
        <IconBar elements={iconBarOrderedDomains} />
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: selectedContract
              ? `60% calc(40% - ${theme.spacing(2)})`
              : '1fr',
            gap: theme.spacing(2),
            overflow: 'hidden',
            padding: `0 0 ${theme.spacing(2.5)} 0`,
          }}
        >
          <div>
            <div style={{
              overflowY: 'auto',
              paddingTop: theme.spacing(0.5),
            }}
            >
              <PaginationComponentV2
                fetch={searchInjectorContracts}
                searchPaginationInput={searchPaginationInput}
                setContent={setContracts}
                entityPrefix="injector_contract"
                availableFilterNames={availableFilterNames}
                queryableHelpers={queryableHelpers}
                attackPatterns={attackPatterns}
              />
              <List>
                <ListItem
                  classes={{ root: classes.itemHead }}
                  divider={false}
                  style={{ paddingTop: 0 }}
                  secondaryAction={<>&nbsp;</>}
                >
                  <ListItemIcon style={{ minWidth: 90 }} />
                  <ListItemText
                    primary={(
                      <SortHeadersComponentV2
                        headers={headers}
                        inlineStylesHeaders={inlineStyles}
                        sortHelpers={queryableHelpers.sortHelpers}
                      />
                    )}
                  />
                  <ListItemIcon />
                </ListItem>
                {contracts.map((contract: InjectorContractFullOutput, index) => {
                  const contractAttackPatterns = computeAttackPatterns(
                    contract.injector_contract_attack_patterns,
                    attackPatternsMap,
                  );

                  const contractKillChainPhase = contractAttackPatterns
                    .flatMap(ap => ap.attack_pattern_kill_chain_phases ?? [])
                    .at(0);

                  const resolvedContractKillChainPhase
                    = contractKillChainPhase && killChainPhasesMap[contractKillChainPhase];

                  return (
                    <ListItem
                      key={contract.injector_contract_id}
                      divider
                      disablePadding
                      secondaryAction={<>&nbsp;</>}
                    >
                      <ListItemButton
                        onClick={() => {
                          selectContract(contract);
                          handleClearSelectedElements();
                        }}
                        selected={selectedContract?.injector_contract_id === contract.injector_contract_id}
                        disabled={selectedContract?.injector_contract_id === contract.injector_contract_id}
                      >
                        {!isAtomic && (
                          <ListItemIcon
                            style={{ minWidth: 40 }}
                            onClick={event => (
                              event.shiftKey
                                ? onRowShiftClick(index, contract, event)
                                : handleToggle(contract, event)
                            )}
                          >
                            <Checkbox
                              edge="start"
                              checked={
                                (selectAll
                                  && !(contract.injector_contract_id in (deSelectedElements || {})))
                                || contract.injector_contract_id in (selectedElements || {})
                              }
                              disableRipple
                            />
                          </ListItemIcon>
                        )}

                        <ListItemIcon style={{ minWidth: 56 }}>
                          <InjectIcon
                            variant="list"
                            type={
                              contract.injector_contract_payload_type
                              ?? contract.injector_contract_injector_type
                            }
                            isPayload={isNotEmptyField(contract.injector_contract_payload_type)}
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
                                  {header.value?.(
                                    contract,
                                    resolvedContractKillChainPhase,
                                    contractAttackPatterns,
                                  )}
                                </div>
                              ))}
                            </div>
                          )}
                        />
                        <ListItemIcon>
                          <KeyboardArrowRight />
                        </ListItemIcon>
                      </ListItemButton>
                    </ListItem>
                  );
                })}

              </List>
            </div>
          </div>
          {selectedContract && numberOfSelectedElements === 0 && (
            <Slide direction="left" in={checked} mountOnEnter unmountOnExit>
              <div style={{
                overflowY: 'auto',
                overflowX: 'hidden',
              }}
              >
                <InjectCardComponent
                  avatar={selectedContract ? (
                    <InjectIcon
                      type={selectedContract.injector_contract_payload_type ?? selectedContract.injector_contract_injector_type}
                      isPayload={isNotEmptyField(selectedContract?.injector_contract_payload_type)}
                    />
                  ) : (
                    <Avatar sx={{
                      width: 24,
                      height: 24,
                    }}
                    >
                      <HelpOutlined />
                    </Avatar>
                  )}
                  title={selectedContractKillChainPhase || selectedContract?.injector_contract_injector_name || ''}
                  action={(
                    <IconButton
                      aria-label="delete"
                      disabled={!selectedContract}
                      onClick={() => setSelectedContract(null)}
                    >
                      <HighlightOffOutlined />
                    </IconButton>
                  )}
                  content={selectedContract?.injector_contract_labels ? tPick(selectedContract?.injector_contract_labels) : t('Select an inject in the left panel')}
                />
                <InjectForm
                  handleClose={handleClose}
                  disabled={!selectedContract}
                  isAtomic={isAtomic}
                  isCreation
                  defaultInject={{
                    inject_id: '',
                    inject_title: tPick(selectedContract?.injector_contract_labels),
                    inject_description: '',
                    inject_depends_duration: presetInjectDuration,
                    inject_injector_contract: {
                      injector_contract_id: selectedContract?.injector_contract_id ?? '',
                      injector_contract_arch: selectedContract?.injector_contract_arch,
                      injector_contract_platforms: selectedContract?.injector_contract_platforms,
                    } as InjectorContract,
                    inject_type: selectedContract?.injector_contract_content?.config?.type,
                    inject_teams: [],
                    inject_assets: [],
                    inject_asset_groups: [],
                    inject_documents: [],
                    inject_content: { expectations: selectedContract?.injector_contract_content.fields.find(f => f.type == 'expectation')?.predefinedExpectations },
                  }}
                  injectorContractContent={selectedContract?.injector_contract_content}
                  onSubmitInject={onCreateInject}
                  articlesFromExerciseOrScenario={articlesFromExerciseOrScenario}
                  uriVariable={uriVariable}
                  variablesFromExerciseOrScenario={variablesFromExerciseOrScenario}
                />
              </div>
            </Slide>
          )}
          {
            numberOfSelectedElements > 0 && (
              <BulkToolBar
                info={t('Bulk select lets you add multiple injects. They\'ll show as "missing content" until configured')}
                numberOfSelectedElements={numberOfSelectedElements}
                handleClearSelectedElements={handleClearSelectedElements}
                toolTasks={toolTasks}
              />
            )
          }
        </div>
      </>
    </Drawer>
  );
};

export default CreateInject;
