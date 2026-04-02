import {Slide} from "@mui/material";
import ScenarioForm from "../scenarios/ScenarioForm";
import {
  type InjectorContractActionOutput, type InjectorContractSearchPaginationInput,
  PlatformSettings, Scenario,
  ScenarioAndInjectorContractsInputs,
  ScenarioInput
} from "../../../utils/api-types";
import {useFormatter} from "../../../components/i18n";
import {useHelper} from "../../../store";
import type {LoggedHelper} from "../../../actions/helper";
import type {AxiosResponse} from "axios";
import {addScenarioWithInjectorContracts} from "../../../actions/scenarios/scenario-actions";
import {useNavigate} from "react-router";

interface Props {
  isExclusionMode: boolean;
  selectedElements: Record<string, InjectorContractActionOutput>;
  deSelectedElements: Record<string, InjectorContractActionOutput>;
  searchPaginationInput: InjectorContractSearchPaginationInput;
}

const ThreatArsenalScenarioCreationComponent = ({ isExclusionMode, selectedElements, deSelectedElements, searchPaginationInput }: Props) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const onSubmit = async (data: ScenarioInput) => {
    const inputs: ScenarioAndInjectorContractsInputs = {
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
          handleClose={() => {}}
          isCreation
        />
      </div>
    </Slide>
  );
};

export default ThreatArsenalScenarioCreationComponent;