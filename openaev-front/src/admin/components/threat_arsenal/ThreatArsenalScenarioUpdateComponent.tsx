import { Slide } from '@mui/material';
import { type AxiosResponse } from 'axios';
import { type SyntheticEvent, useState } from 'react';
import { useNavigate } from 'react-router';

import { updateScenariosWithInjectorContracts } from '../../../actions/scenarios/scenario-actions';
import Button from '../../../components/common/button/Button';
import ScenariosField, { type MultiSelectScenario } from '../../../components/fields/ScenariosField';
import { useFormatter } from '../../../components/i18n';
import {
  type InjectorContractActionOutput,
  type InjectorContractSearchPaginationInput,
  type Scenario,
  type ScenarioIdsAndInjectorContractsInputs,
} from '../../../utils/api-types';

interface Props {
  isExclusionMode: boolean;
  selectedElements: Record<string, InjectorContractActionOutput>;
  deSelectedElements: Record<string, InjectorContractActionOutput>;
  searchPaginationInput: InjectorContractSearchPaginationInput;
  handleClose: () => void;
}

const ThreatArsenalScenarioUpdateComponent = ({ isExclusionMode, selectedElements, deSelectedElements, searchPaginationInput, handleClose }: Props) => {
  const { t, locale } = useFormatter();
  const navigate = useNavigate();

  const [scenarioValues, setScenarioValues] = useState<MultiSelectScenario[]>([]);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const handleSubmit = () => {
    setIsSubmitting(true);
    const inputs: ScenarioIdsAndInjectorContractsInputs = {
      locale: locale,
      scenario_ids: scenarioValues.map(scenario => scenario.id),
      injector_contract_search_pagination_input: {
        ...searchPaginationInput,
        injector_contract_ids_to_process: isExclusionMode ? [] : Object.keys(selectedElements),
        injector_contract_ids_to_ignore: isExclusionMode ? Object.keys(deSelectedElements) : [],
      },
    };
    updateScenariosWithInjectorContracts(inputs).then((result: AxiosResponse<Scenario[]>) => {
      navigate(`/admin/scenarios/${result.data[0].scenario_id}/injects`);
    }).finally(() => setIsSubmitting(false));
  };

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit();
  };

  return (
    <Slide in={true} direction="left" mountOnEnter unmountOnExit>
      <div style={{
        overflowY: 'auto',
        overflowX: 'hidden',
      }}
      >
        <form id="threatArsenalScenarioUpdateForm" onSubmit={handleSubmitWithoutPropagation}>
          <ScenariosField
            label={t('Scenario')}
            value={scenarioValues}
            onChange={newValue => setScenarioValues(newValue)}
          />
          <div style={{
            float: 'right',
            marginTop: 20,
          }}
          >
            <Button
              variant="secondary"
              onClick={handleClose}
              style={{ marginRight: 10 }}
              disabled={isSubmitting}
            >
              {t('Cancel')}
            </Button>
            <Button
              variant="primary"
              type="submit"
              // onClick={handleSubmit}
              disabled={scenarioValues.length === 0 || isSubmitting}
            >
              {t('Create')}
            </Button>
          </div>
        </form>
      </div>
    </Slide>
  );
};

export default ThreatArsenalScenarioUpdateComponent;
