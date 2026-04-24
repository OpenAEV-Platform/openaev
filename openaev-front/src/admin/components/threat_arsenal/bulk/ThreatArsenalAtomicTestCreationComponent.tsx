import { Slide } from '@mui/material';
import type { AxiosResponse } from 'axios';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { createAtomicTesting } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { searchThreatArsenalActions } from '../../../../actions/threat_arsenals/ThreatArsenal-actions';
import { useFormatter } from '../../../../components/i18n';
import {
  type InjectInput,
  type InjectorContract,
  type InjectorContractSearchPaginationInput,
  type ThreatArsenalAction, type ThreatArsenalActionWithContentOutput,
} from '../../../../utils/api-types';
import { type ContractElement, type ThreatArsenalContentConverted } from '../../../../utils/api-types-custom';
import { EndpointContext } from '../../../../utils/context/endpoint/EndpointContext';
import endpointContextForAtomicTesting from '../../../../utils/context/endpoint/EndpointContextForAtomicTesting';
import { isNotEmptyField } from '../../../../utils/utils';
import teamContextForAtomicTesting from '../../atomic_testings/atomic_testing/context/TeamContextForAtomicTesting';
import { TeamContext } from '../../common/Context';
import InjectForm from '../../common/injects/form/InjectForm';
import InjectCardComponent from '../../common/injects/InjectCardComponent';
import InjectIcon from '../../common/injects/InjectIcon';

interface Props {
  isExclusionMode: boolean;
  selectedElements: Record<string, ThreatArsenalAction>;
  deSelectedElements: Record<string, ThreatArsenalAction>;
  searchPaginationInput: InjectorContractSearchPaginationInput;
  handleClose: () => void;
}

const ThreatArsenalAtomicTestCreationComponent = ({ isExclusionMode, selectedElements, deSelectedElements, searchPaginationInput, handleClose }: Props) => {
  const { tPick } = useFormatter();
  const navigate = useNavigate();

  const [selectedAction, setSelectedAction] = useState<ThreatArsenalContentConverted | null>(null);
  const [isLoading, setLoading] = useState<boolean>(false);

  const onSubmitInject = async (data: InjectInput) => {
    setLoading(true);
    const result = await createAtomicTesting(data);
    return navigate(`/admin/atomic_testings/${result.data.inject_id}`);
  };

  useEffect(() => {
    searchThreatArsenalActions({
      ...searchPaginationInput,
      include_content_details: true,
      injector_contract_ids_to_process: isExclusionMode ? [] : Object.keys(selectedElements),
      injector_contract_ids_to_ignore: isExclusionMode ? Object.keys(deSelectedElements) : [],
    }).then((response: AxiosResponse<{ content: ThreatArsenalActionWithContentOutput[] }>) => {
      if (response?.data?.content?.[0]) {
        setSelectedAction({
          ...response.data.content[0],
          convertedContent: typeof response.data.content[0]?.action_content === 'string'
            ? JSON.parse(response.data.content[0].action_content)
            : {},
        });
      }
    });
  }, []);

  return (
    <Slide in={true} direction="left" mountOnEnter unmountOnExit>
      <div style={{
        overflowY: 'auto',
        overflowX: 'hidden',
      }}
      >
        {selectedAction && (
          <>
            <InjectCardComponent
              avatar={(
                <InjectIcon
                  type={selectedAction.action_payload_type ?? selectedAction.action_injector_type}
                  isPayload={isNotEmptyField(selectedAction.action_payload_type)}
                />
              )}
              title={selectedAction.action_injector_name ?? ''}
              content={tPick(selectedAction.action_labels)}
              action={<></>}
            />
            <TeamContext.Provider value={teamContextForAtomicTesting()}>
              <EndpointContext.Provider value={endpointContextForAtomicTesting()}>
                <InjectForm
                  handleClose={handleClose}
                  disabled={!selectedAction || isLoading}
                  isAtomic={true}
                  isCreation={true}
                  defaultInject={{
                    inject_id: '',
                    inject_title: tPick(selectedAction.action_labels),
                    inject_description: '',
                    inject_depends_duration: 0,
                    inject_injector_contract: {
                      injector_contract_id: selectedAction.injector_contract_id ?? '',
                      injector_contract_arch: selectedAction.action_arch,
                      injector_contract_platforms: selectedAction.action_platforms,
                      injector_contract_content: selectedAction.action_content,
                      injector_contract_created_at: '',
                      injector_contract_updated_at: '',
                    } as InjectorContract,
                    inject_type: selectedAction.convertedContent?.config?.type,
                    inject_teams: [],
                    inject_assets: [],
                    inject_asset_groups: [],
                    inject_documents: [],
                    inject_content: { expectations: selectedAction.convertedContent.fields.find((f: ContractElement) => f.type === 'expectation')?.predefinedExpectations },
                  }}
                  injectorContractContent={selectedAction.convertedContent}
                  onSubmitInject={onSubmitInject}
                  uriVariable=""
                  articlesFromExerciseOrScenario={[]}
                  variablesFromExerciseOrScenario={[]}
                />
              </EndpointContext.Provider>
            </TeamContext.Provider>
          </>
        )}
      </div>
    </Slide>
  );
};

export default ThreatArsenalAtomicTestCreationComponent;
