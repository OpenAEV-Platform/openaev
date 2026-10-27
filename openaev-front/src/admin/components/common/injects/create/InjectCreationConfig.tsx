import { Alert } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, useEffect, useRef, useState } from 'react';

import { type AttackPatternHelper } from '../../../../../actions/attack_patterns/attackpattern-helper';
import { fetchAttackPatterns } from '../../../../../actions/AttackPattern';
import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import { type KillChainPhaseHelper } from '../../../../../actions/kill_chain_phases/killchainphase-helper';
import { fetchKillChainPhases } from '../../../../../actions/KillChainPhase';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import {
  type Article,
  type AtomicTestingInput,
  type AttackPattern,
  type InjectInput,
  type InjectorContract,
  type KillChainPhase,
  type Variable,
} from '../../../../../utils/api-types';
import { type InjectorContractConverted } from '../../../../../utils/api-types-custom';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import computeAttackPatterns from '../../../../../utils/injector_contract/InjectorContractUtils';
import { isNotEmptyField } from '../../../../../utils/utils';
import PayloadContractVersionSelect from '../../../threat_arsenal/PayloadContractVersionSelect';
import InjectForm from '../form/InjectForm';
import InjectCardComponent from '../InjectCardComponent';
import InjectIcon from '../InjectIcon';

interface Props {
  contractId: string;
  isAtomic?: boolean;
  onCreateInject: (data: InjectInput | AtomicTestingInput) => Promise<void>;
  /** Back to the contract picker (closes the drawer; also the form cancel). */
  onBack: () => void;
  presetInjectDuration?: number;
  articlesFromExerciseOrScenario?: Article[];
  uriVariable?: string;
  variablesFromExerciseOrScenario?: Variable[];
}

// Mandatory fields of the new version that the previous one did not have, without a default.
const newMandatoryArguments = (previous: InjectorContract, next: InjectorContract): string[] => {
  const fields = (contract: InjectorContract) => (JSON.parse(contract.injector_contract_content) as InjectorContractConverted['convertedContent'])?.fields ?? [];
  const previousKeys = new Set(fields(previous).map(field => field.key));
  return fields(next)
    .filter(field => field.mandatory && !previousKeys.has(field.key) && (field.defaultValue ?? []).length === 0)
    .map(field => field.label ?? field.key);
};

// Step 2 of the inject creation flow: the selected contract is fetched and the
// full configuration form renders inside a drawer over the contract picker.
const InjectCreationConfig: FunctionComponent<Props> = ({
  contractId,
  isAtomic = false,
  onCreateInject,
  onBack,
  presetInjectDuration = 0,
  articlesFromExerciseOrScenario = [],
  uriVariable = '',
  variablesFromExerciseOrScenario = [],
}) => {
  const { t, tPick } = useFormatter();
  const dispatch = useAppDispatch();

  // InjectForm always calls handleClose() after a successful submit. Here the
  // submit callback navigates to the created entity, so the close-to-picker
  // callback must be skipped or it clobbers the redirect.
  const submittedRef = useRef(false);
  const handleSubmit = async (data: InjectInput | AtomicTestingInput) => {
    submittedRef.current = true;
    try {
      await onCreateInject(data);
    } catch (error) {
      submittedRef.current = false;
      throw error;
    }
  };
  const handleClose = () => {
    if (!submittedRef.current) {
      onBack();
    }
  };

  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
    dispatch(fetchKillChainPhases());
  });

  const {
    attackPatternsMap,
    killChainPhasesMap,
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper) => ({
    attackPatternsMap: helper.getAttackPatternsMap(),
    killChainPhasesMap: helper.getKillChainPhasesMap(),
  })) as {
    attackPatternsMap: Record<string, AttackPattern>;
    killChainPhasesMap: Record<string, KillChainPhase>;
  };

  const [activeContractId, setActiveContractId] = useState(contractId);
  const [contract, setContract] = useState<InjectorContract | null>(null);
  const [missingArguments, setMissingArguments] = useState<string[]>([]);
  const [selectedInjectorName, setSelectedInjectorName] = useState<string>('');
  useEffect(() => {
    directFetchInjectorContract(activeContractId).then((result: { data: InjectorContract }) => {
      setContract((previous) => {
        setMissingArguments(previous ? newMandatoryArguments(previous, result.data) : []);
        return result.data;
      });
      const names = result.data.injector_contract_injector_names;
      setSelectedInjectorName(names ? Object.values(names)[0] ?? '' : '');
    });
  }, [activeContractId]);

  if (!contract) {
    return <Loader variant="inElement" />;
  }

  const parsedContent = JSON.parse(contract.injector_contract_content) as InjectorContractConverted['convertedContent'];
  const payloadType = contract.injector_contract_payload
    ? contract.injector_contract_payload.payload_collector_type ?? contract.injector_contract_payload.payload_type
    : contract.injector_contract_injector_type;

  const contractAttackPatterns = computeAttackPatterns(contract.injector_contract_attack_patterns, attackPatternsMap);
  const killChainPhaseId = contractAttackPatterns
    .flatMap((attackPattern: AttackPattern) => attackPattern.attack_pattern_kill_chain_phases ?? [])
    .at(0);
  const killChainPhaseLabel = killChainPhaseId && killChainPhasesMap[killChainPhaseId]
    ? `${killChainPhasesMap[killChainPhaseId].phase_name} / ${contractAttackPatterns.map((attackPattern: AttackPattern) => attackPattern.attack_pattern_external_id).join(', ')}`
    : null;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      width: '100%',
      paddingBottom: 4,
    }}
    >
      <InjectCardComponent
        avatar={(
          <InjectIcon
            type={payloadType}
            isPayload={isNotEmptyField(contract.injector_contract_payload)}
          />
        )}
        title={killChainPhaseLabel || selectedInjectorName || ''}
        action={contract.injector_contract_payload
          ? (
              <PayloadContractVersionSelect
                contractId={contract.injector_contract_id}
                onChange={version => setActiveContractId(version.payload_action_id as string)}
              />
            )
          : null}
        content={tPick(contract.injector_contract_labels)}
      />
      {missingArguments.length > 0 && (
        <Alert severity="warning" title={`${t('This version needs values for')} ${missingArguments.join(', ')}`} />
      )}
      <InjectForm
        handleClose={handleClose}
        isAtomic={isAtomic}
        isCreation
        defaultInject={{
          inject_id: '',
          inject_title: tPick(contract.injector_contract_labels),
          inject_description: '',
          inject_depends_duration: presetInjectDuration,
          inject_injector_contract: {
            injector_contract_id: contract.injector_contract_id,
            injector_contract_arch: contract.injector_contract_arch,
            injector_contract_platforms: contract.injector_contract_platforms,
            injector_contract_content: '',
            injector_contract_created_at: '',
            injector_contract_updated_at: '',
          } as InjectorContract,
          inject_type: parsedContent?.config?.type,
          inject_teams: [],
          inject_assets: [],
          inject_asset_groups: [],
          inject_secret_references: [],
          inject_documents: [],
          inject_content: { expectations: parsedContent?.fields?.find(f => f.type === 'expectation')?.availableExpectations?.filter(e => e.expectation_is_predefined) },
        }}
        injectorContractContent={parsedContent}
        onSubmitInject={handleSubmit}
        articlesFromExerciseOrScenario={articlesFromExerciseOrScenario}
        uriVariable={uriVariable}
        variablesFromExerciseOrScenario={variablesFromExerciseOrScenario}
        injectorNames={contract.injector_contract_injector_names}
        onInjectorChange={(_id, name) => setSelectedInjectorName(name)}
      />
    </Box>
  );
};

export default InjectCreationConfig;
