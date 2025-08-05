import { HelpOutlined, HighlightOffOutlined, KeyboardArrowRight } from '@mui/icons-material';
import {
  Avatar,
  Chip, IconButton,
  List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { searchInjectorContracts } from '../../../../actions/InjectorContracts';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import { type KillChainPhaseHelper } from '../../../../actions/kill_chain_phases/killchainphase-helper';
import Drawer from '../../../../components/common/Drawer';
import { buildEmptyFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useHelper } from '../../../../store';
import {
  type Article,
  type AtomicTestingInput,
  type AttackPattern,
  type FilterGroup,
  type InjectInput, type InjectorContract,
  type InjectorContractFullOutput,
  type KillChainPhase, type Variable,
} from '../../../../utils/api-types';
import { type InjectorContractConverted } from '../../../../utils/api-types-custom';
import computeAttackPatterns from '../../../../utils/injector_contract/InjectorContractUtils';
import { isNotEmptyField } from '../../../../utils/utils';
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
  kill_chain_phase: { width: '20%' },
  injector_contract_labels: { width: '45%' },
  injector_contract_platforms: { width: '15%' },
  attack_patterns: { width: '20%' },
};

interface Props {
  title: string;
  onCreateInject: (data: InjectInput | AtomicTestingInput) => Promise<void>;
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

  // Fetching data
  const { attackPatterns, attackPatternsMap, killChainPhasesMap } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper & InjectorHelper) => ({
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
      label: 'Label',
      isSortable: false,
      value: (contract: InjectorContractFullOutput, _: KillChainPhase, __: Record<string, AttackPattern>) => (
        <Tooltip title={tPick(contract.injector_contract_labels)}>
          <span>{tPick(contract.injector_contract_labels)}</span>
        </Tooltip>
      ),
    },
    {
      field: 'injector_contract_platforms',
      label: 'Platforms',
      isSortable: false,
      value: (contract: InjectorContractFullOutput, _: KillChainPhase, __: Record<string, AttackPattern>) => (
        <>
          {(contract.injector_contract_platforms ?? []).map(
            (platform: string) => <PlatformIcon key={platform} width={20} platform={platform} marginRight={theme.spacing(2)} />,
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
  const quickFilter: FilterGroup = {
    mode: 'and',
    filters: [
      buildEmptyFilter('injector_contract_kill_chain_phases', 'contains'),
      buildEmptyFilter('injector_contract_injector', 'contains'),
      buildEmptyFilter('injector_contract_platforms', 'contains'),
    ],
  };

  const addAtomicFilter = (filterGroup: FilterGroup) => {
    const filters = filterGroup.filters ?? [];
    if (filters.map(f => f.key).includes('injector_contract_atomic_testing')) {
      return filterGroup;
    }

    filters.push({
      key: 'injector_contract_atomic_testing',
      operator: 'eq',
      values: ['true'],
    });

    return {
      ...filterGroup,
      filters,
    };
  };

  const availableFilterNames = [
    'injector_contract_attack_patterns',
    'injector_contract_injector',
    'injector_contract_kill_chain_phases',
    'injector_contract_labels',
    'injector_contract_platforms',
    'injector_contract_players',
    'injector_contract_arch',
  ];

  // Contracts
  const [contracts, setContracts] = useState<InjectorContractFullOutput[]>([]);
  const initSearchPaginationInput = () => {
    return ({
      sorts: initSorting('injector_contract_labels'),
      filterGroup: isAtomic ? addAtomicFilter(quickFilter) : quickFilter,
      size: 100,
      page: 0,
    });
  };

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(isAtomic ? 'injector-contracts-atomic' : 'injector-contracts', initSearchPaginationInput());

  const [selectedContract, setSelectedContract] = useState<Omit<InjectorContractFullOutput, 'injector_contract_content'> & { injector_contract_content: InjectorContractConverted['convertedContent'] } | null>(null);
  const selectContract = (contract: InjectorContractFullOutput) => {
    const parsedContract: Omit<InjectorContractFullOutput, 'injector_contract_content'> & { injector_contract_content: InjectorContractConverted['convertedContent'] } = {
      ...contract,
      injector_contract_content: JSON.parse(contract.injector_contract_content),
    };
    setSelectedContract(parsedContract);
  };

  const handleCloseDrawer = () => {
    setSelectedContract(null);
    handleClose();
  };

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

  return (
    <Drawer
      open={open}
      handleClose={handleCloseDrawer}
      title={title}
      variant="full"
      disableEnforceFocus
      containerStyle={{
        display: 'grid',
        gridTemplateColumns: `60% calc(40% - ${theme.spacing(2)})`,
        gap: theme.spacing(2),
      }}
    >
      <>
        <div>
          <PaginationComponentV2
            fetch={searchInjectorContracts}
            searchPaginationInput={searchPaginationInput}
            setContent={setContracts}
            entityPrefix="injector_contract"
            availableFilterNames={availableFilterNames}
            queryableHelpers={queryableHelpers}
            disablePagination
            attackPatterns={attackPatterns}
          />
          <List>
            <ListItem
              classes={{ root: classes.itemHead }}
              divider={false}
            >
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
              <ListItemIcon />
            </ListItem>
            {contracts.map((contract) => {
              const contractAttackPatterns = computeAttackPatterns(contract.injector_contract_attack_patterns, attackPatternsMap);
              const contractKillChainPhase = contractAttackPatterns
                .flatMap((contractAttackPattern: AttackPattern) => contractAttackPattern.attack_pattern_kill_chain_phases ?? [])
                .at(0);
              const resolvedContractKillChainPhase = contractKillChainPhase && killChainPhasesMap[contractKillChainPhase];
              return (
                <ListItemButton
                  key={contract.injector_contract_id}
                  divider
                  onClick={() => selectContract(contract)}
                  selected={selectedContract?.injector_contract_id === contract.injector_contract_id}
                  disabled={(selectedContract?.injector_contract_id === contract.injector_contract_id)}
                >
                  <ListItemIcon>
                    <InjectIcon
                      variant="list"
                      type={contract.injector_contract_payload_type ?? contract.injector_contract_injector_type}
                      isPayload={isNotEmptyField(contract.injector_contract_payload_type)}
                    />
                  </ListItemIcon>
                  {headers.map(header => (
                    <div
                      key={header.field}
                      className={classes.bodyItem}
                      style={inlineStyles[header.field]}
                    >
                      {header.value?.(contract, resolvedContractKillChainPhase, contractAttackPatterns)}
                    </div>
                  ))}
                  <ListItemIcon>
                    <KeyboardArrowRight />
                  </ListItemIcon>
                </ListItemButton>
              );
            })}
          </List>
        </div>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
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
              <IconButton aria-label="delete" disabled={!selectedContract} onClick={() => setSelectedContract(null)}>
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
      </>
    </Drawer>
  );
};

export default CreateInject;
