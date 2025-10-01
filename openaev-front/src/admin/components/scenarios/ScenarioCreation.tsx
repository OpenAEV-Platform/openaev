import { type FunctionComponent, useState } from 'react';
import { useNavigate } from 'react-router';

import { type LoggedHelper } from '../../../actions/helper';
import { addScenario } from '../../../actions/scenarios/scenario-actions';
import ButtonCreate from '../../../components/common/ButtonCreate';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { SCENARIO_BASE_URL } from '../../../constants/BaseUrls';
import { useHelper } from '../../../store';
import { type PlatformSettings, type Scenario, type ScenarioInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import ScenarioForm from './ScenarioForm';

const ScenarioCreation: FunctionComponent = () => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const navigate = useNavigate();

  const dispatch = useAppDispatch();

  const onSubmit = (data: ScenarioInput, isScenarioAssistantChecked?: boolean) => {
    dispatch(addScenario(data)).then(
      (result: {
        result: string;
        entities: { scenarios: Record<string, Scenario> };
      }) => {
        if (result.entities) {
          navigate(`${SCENARIO_BASE_URL}/${result.result}?openScenarioAssistant=${isScenarioAssistantChecked}`);
          setOpen(false);
        }
      },
    );
  };

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

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
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new scenario')}
      >
        <ScenarioForm
          onSubmit={onSubmit}
          initialValues={initialValues}
          handleClose={() => setOpen(false)}
          isCreation
        />
      </Drawer>
    </>
  );
};
export default ScenarioCreation;
