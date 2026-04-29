import { Button, Slide } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type AxiosResponse } from 'axios';
import { type SyntheticEvent, useState } from 'react';
import { useNavigate } from 'react-router';

import { updateScenariosWithInjectorContracts } from '../../../../actions/scenarios/scenario-actions';
import ScenarioField from '../../../../components/fields/ScenarioField';
import { useFormatter } from '../../../../components/i18n';
import {
  type InjectorContractSearchPaginationInput,
  type Scenario,
  type ScenarioIdsAndInjectorContractsInputs,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import type { Option } from '../../../../utils/Option';

interface Props {
  isExclusionMode: boolean;
  selectedElements: Record<string, ThreatArsenalAction>;
  deSelectedElements: Record<string, ThreatArsenalAction>;
  searchPaginationInput: InjectorContractSearchPaginationInput;
  handleClose: () => void;
}

const ThreatArsenalScenarioUpdateComponent = ({ isExclusionMode, selectedElements, deSelectedElements, searchPaginationInput, handleClose }: Props) => {
  const { t, locale } = useFormatter();
  const navigate = useNavigate();
  const theme = useTheme();

  const [scenarioValues, setScenarioValues] = useState<Option[] | null>([]);
  const [isSubmitting, setSubmitting] = useState<boolean>(false);

  const handleSubmit = () => {
    setSubmitting(true);
    const inputs: ScenarioIdsAndInjectorContractsInputs = {
      locale: locale,
      scenario_ids: scenarioValues?.map(scenario => scenario.id) ?? [],
      injector_contract_search_pagination_input: {
        ...searchPaginationInput,
        injector_contract_ids_to_process: isExclusionMode ? [] : Object.keys(selectedElements),
        injector_contract_ids_to_ignore: isExclusionMode ? Object.keys(deSelectedElements) : [],
      },
    };
    updateScenariosWithInjectorContracts(inputs).then((result: AxiosResponse<Scenario[]>) => {
      navigate(`/admin/scenarios/${result.data[0].scenario_id}/injects`);
    }).finally(() => setSubmitting(false));
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
          <ScenarioField
            multiple={true}
            label={t('Scenario')}
            values={scenarioValues ?? []}
            onValuesChange={(newValue: Option[] | null) => setScenarioValues(newValue)}
          />
          <div style={{
            float: 'right',
            marginTop: theme.spacing(2),
          }}
          >
            <Button
              variant="contained"
              onClick={handleClose}
              style={{ marginRight: theme.spacing(1) }}
              disabled={isSubmitting}
            >
              {t('Cancel')}
            </Button>
            <Button
              variant="contained"
              type="submit"
              color="secondary"
              disabled={scenarioValues?.length === 0 || isSubmitting}
            >
              {t('Update')}
            </Button>
          </div>
        </form>
      </div>
    </Slide>
  );
};

export default ThreatArsenalScenarioUpdateComponent;
