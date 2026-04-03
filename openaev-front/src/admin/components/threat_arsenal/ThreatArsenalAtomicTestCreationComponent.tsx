import { HelpOutlined } from '@mui/icons-material';
import { Avatar, Slide } from '@mui/material';
import type { AxiosResponse } from 'axios';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { createAtomicTesting } from '../../../actions/atomic_testings/atomic-testing-actions';
import { searchInjectorContracts } from '../../../actions/InjectorContracts';
import { useFormatter } from '../../../components/i18n';
import {
  type InjectInput,
  type InjectorContract,
  type InjectorContractActionOutput,
  type InjectorContractFullOutput,
  type InjectorContractSearchPaginationInput,
} from '../../../utils/api-types';
import type { InjectorContractConverted } from '../../../utils/api-types-custom';
import { isNotEmptyField } from '../../../utils/utils';
import InjectForm from '../common/injects/form/InjectForm';
import InjectCardComponent from '../common/injects/InjectCardComponent';
import InjectIcon from '../common/injects/InjectIcon';

interface Props {
  isExclusionMode: boolean;
  selectedElements: Record<string, InjectorContractActionOutput>;
  deSelectedElements: Record<string, InjectorContractActionOutput>;
  searchPaginationInput: InjectorContractSearchPaginationInput;
  handleClose: () => void;
}

type InjectorContractFullOutputWithContractContent = InjectorContractFullOutput & { injector_contract_content: InjectorContractConverted['convertedContent'] };

const ThreatArsenalAtomicTestCreationComponent = ({ isExclusionMode, selectedElements, deSelectedElements, searchPaginationInput, handleClose }: Props) => {
  const { t, tPick } = useFormatter();
  const navigate = useNavigate();

  const [selectedContract, setSelectedContract] = useState<InjectorContractFullOutputWithContractContent | null>(null);

  const onSubmitInject = async (data: InjectInput) => {
    const result = await createAtomicTesting(data);
    return navigate(`/admin/atomic_testings/${result.data.inject_id}/injects`);
  };

  useEffect(() => {
    searchInjectorContracts({
      ...searchPaginationInput,
      injector_contract_ids_to_process: isExclusionMode ? [] : Object.keys(selectedElements),
      injector_contract_ids_to_ignore: isExclusionMode ? Object.keys(deSelectedElements) : [],
    }).then((response: AxiosResponse<{ content: InjectorContractFullOutputWithContractContent[] }>) => {
      if (response?.data?.content?.[0]) {
        const selectedContract = response.data.content[0];
        selectedContract.injector_contract_content = typeof selectedContract.injector_contract_content === 'string'
          ? JSON.parse(selectedContract.injector_contract_content)
          : selectedContract.injector_contract_content;
        setSelectedContract(response?.data?.content[0]);
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
          title={selectedContract?.injector_contract_injector_name || ''}
          content={selectedContract?.injector_contract_labels ? tPick(selectedContract?.injector_contract_labels) : t('Select an inject in the left panel')}
          action={<></>}
        />
        <InjectForm
          handleClose={handleClose}
          disabled={!selectedContract}
          isAtomic={true}
          isCreation
          defaultInject={{
            inject_id: '',
            inject_title: tPick(selectedContract?.injector_contract_labels),
            inject_description: '',
            inject_depends_duration: 0,
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
          onSubmitInject={onSubmitInject}
          uriVariable=""
          articlesFromExerciseOrScenario={[]}
          variablesFromExerciseOrScenario={[]}
        />
      </div>
    </Slide>
  );
};

export default ThreatArsenalAtomicTestCreationComponent;
