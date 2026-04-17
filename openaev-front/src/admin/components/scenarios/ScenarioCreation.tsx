import { type FunctionComponent, type ReactElement, useCallback, useState } from 'react';
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
import { isFeatureEnabled } from '../../../utils/utils';
import EngineTypeSelection, { type EngineType } from '../common/EngineTypeSelection';
import ScenarioForm from './ScenarioForm';
import ScenarioFormChaining from './ScenarioFormChaining';

const ScenarioCreation: FunctionComponent = () => {
  // Standard hooks
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const [open, setOpen] = useState(false);
  const [engineType, setEngineType] = useState<EngineType>(isChainingFeatureEnabled ? null : 'time-based');
  const { t } = useFormatter();
  const navigate = useNavigate();

  const dispatch = useAppDispatch();

  const handleTypeSelected = useCallback((type: EngineType) => {
    setEngineType(type);
  }, []);

  const onSubmit = (data: ScenarioInput, isScenarioAssistantChecked?: boolean) => {
    dispatch(addScenario({
      ...data,
      scenario_is_chaining: engineType === 'chaining',
    })).then(
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
    scenario_mail_from_name: settings.default_mailer_name ?? '',
    scenario_mails_reply_to: [settings.default_reply_to ?? ''],
  };
  const renderDrawerContent = (): ReactElement => {
    // if feature flag is disabled we just display the old form
    if (!isChainingFeatureEnabled) {
      return (
        <ScenarioForm
          onSubmit={onSubmit}
          initialValues={initialValues}
          handleClose={() => setOpen(false)}
          isCreation
        />
      );
    }

    // if scenario type is selected (standard or chaining), then display the form otherwise display the scenario type selection
    return (
      <>
        <EngineTypeSelection
          selected={engineType}
          onSelect={handleTypeSelected}
        />
        {engineType !== null && (
          <ScenarioFormChaining
            onSubmit={onSubmit}
            initialValues={initialValues}
            handleClose={() => setOpen(false)}
            isChaining={engineType === 'chaining'}
          />
        )}
      </>
    );
  };
  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new scenario')}
      >
        {renderDrawerContent}
      </Drawer>
    </>
  );
};
export default ScenarioCreation;
