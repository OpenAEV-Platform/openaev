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
import EngineTypeSelection from '../common/EngineTypeSelection';
import ScenarioForm from './ScenarioForm';

type CreationStep = 'type-selection' | 'form';

const ScenarioCreation: FunctionComponent = () => {
  // Standard hooks
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const [open, setOpen] = useState(false);
  const [creationStep, setCreationStep] = useState<CreationStep>(isChainingFeatureEnabled ? 'type-selection' : 'form');
  const [isChaining, setIsChaining] = useState<boolean>(false);
  const { t } = useFormatter();
  const navigate = useNavigate();

  const dispatch = useAppDispatch();

  const handleClose = useCallback(() => {
    setOpen(false);
    setCreationStep(isChainingFeatureEnabled ? 'type-selection' : 'form');
    setIsChaining(false);
  }, [isChainingFeatureEnabled]);

  const handleTypeSelected = useCallback((chaining: boolean) => {
    setIsChaining(chaining);
    setCreationStep('form');
  }, []);

  const onSubmit = (data: ScenarioInput, isScenarioAssistantChecked?: boolean) => {
    const payload: ScenarioInput = {
      ...data,
      scenario_is_chaining: isChaining,
    };
    dispatch(addScenario(payload)).then(
      (result: {
        result: string;
        entities: { scenarios: Record<string, Scenario> };
      }) => {
        if (result.entities) {
          navigate(`${SCENARIO_BASE_URL}/${result.result}?openScenarioAssistant=${isScenarioAssistantChecked}`);
          handleClose();
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

  const drawerTitle = (creationStep === 'type-selection' && isChainingFeatureEnabled)
    ? t('Create a new scenario')
    : isChaining
      ? `${t('Create a new scenario')} — ${t('Chaining')}`
      : isChainingFeatureEnabled
        ? `${t('Create a new scenario')} — ${t('Time-based')}`
        : t('Create a new scenario');

  const renderDrawerContent = (): ReactElement => {
    if (creationStep === 'type-selection' && isChainingFeatureEnabled) {
      return (
        <EngineTypeSelection
          onSelect={handleTypeSelected}
          onCancel={handleClose}
        />
      );
    }
    return (
      <ScenarioForm
        onSubmit={onSubmit}
        initialValues={initialValues}
        handleClose={handleClose}
        isCreation
      />
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={drawerTitle}
      >
        {renderDrawerContent}
      </Drawer>
    </>
  );
};
export default ScenarioCreation;
