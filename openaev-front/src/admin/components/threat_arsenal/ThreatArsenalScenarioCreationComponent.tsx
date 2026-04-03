import { Slide } from '@mui/material';
import { useNavigate } from 'react-router';

import type { LoggedHelper } from '../../../actions/helper';
import { addScenarioWithInjectorContracts } from '../../../actions/scenarios/scenario-actions';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import {
  type InjectorContractActionOutput,
  type InjectorContractSearchPaginationInput,
  type PlatformSettings,
  type ScenarioAndInjectorContractsInputs,
  type ScenarioInput,
} from '../../../utils/api-types';
import ScenarioForm from '../scenarios/ScenarioForm';

interface Props {
  isExclusionMode: boolean;
  selectedElements: Record<string, InjectorContractActionOutput>;
  deSelectedElements: Record<string, InjectorContractActionOutput>;
  searchPaginationInput: InjectorContractSearchPaginationInput;
  handleClose: () => void;
}

const ThreatArsenalScenarioCreationComponent = ({ isExclusionMode, selectedElements, deSelectedElements, searchPaginationInput, handleClose }: Props) => {
  const { t, locale } = useFormatter();
  const navigate = useNavigate();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const onSubmit = async (data: ScenarioInput) => {
    const inputs: ScenarioAndInjectorContractsInputs = {
      locale: locale,
      scenario_input: data,
      injector_contract_search_pagination_input: {
        ...searchPaginationInput,
        injector_contract_ids_to_process: isExclusionMode ? [] : Object.keys(selectedElements),
        injector_contract_ids_to_ignore: isExclusionMode ? Object.keys(deSelectedElements) : [],
      },
    };
    const result = await addScenarioWithInjectorContracts(inputs);
    navigate(`/admin/scenarios/${result.data.scenario_id}/injects`);
  };

  const initialValues: ScenarioInput = {
    scenario_name: '',
    scenario_category: 'attack-scenario',
    scenario_main_focus: 'incident-response',
    scenario_severity: 'high',
    scenario_subtitle: '',
    scenario_description: '',
    scenario_external_reference: '',
    scenario_external_url: '',
    scenario_tags: [],
    scenario_message_header: t('SIMULATION HEADER'),
    scenario_message_footer: t('SIMULATION FOOTER'),
    scenario_mail_from: settings.default_mailer ?? '',
    scenario_mails_reply_to: [settings.default_reply_to ?? ''],
  };

  return (
    <Slide in={true} direction="left" mountOnEnter unmountOnExit>
      <div style={{
        overflowY: 'auto',
        overflowX: 'hidden',
      }}
      >
        <ScenarioForm
          onSubmit={onSubmit}
          initialValues={initialValues}
          handleClose={handleClose}
          isCreation
        />
      </div>
    </Slide>
  );
};

export default ThreatArsenalScenarioCreationComponent;
