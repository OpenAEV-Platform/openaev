import { type FunctionComponent, useState } from 'react';

import { checkScenarioTagRules, updateScenario } from '../../../../actions/scenarios/scenario-actions';
import DialogApplyTagRule from '../../../../components/common/DialogApplyTagRule';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import {
  type CheckScenarioRulesOutput,
  type Scenario,
  type UpdateScenarioInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import ScenarioForm from '../ScenarioForm';

interface Props {
  scenario: Scenario;
  open: boolean;
  handleClose: () => void;
}

const ScenarioUpdate: FunctionComponent<Props> = ({
  scenario,
  open,
  handleClose,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const permissions = useScenarioPermissions(scenario.scenario_id);

  // apply rule dialog
  const [openApplyRule, setOpenApplyRule] = useState(false);
  const handleOpenApplyRule = () => setOpenApplyRule(true);
  const handleCloseApplyRule = () => setOpenApplyRule(false);

  // Scenario form
  const initialValues = (({
    scenario_name,
    scenario_subtitle,
    scenario_description,
    scenario_category,
    scenario_main_focus,
    scenario_severity,
    scenario_tags,
    scenario_external_reference,
    scenario_external_url,
    scenario_message_header,
    scenario_message_footer,
    scenario_mail_from,
    scenario_mails_reply_to,
    scenario_custom_dashboard,
  }) => ({
    scenario_name,
    scenario_subtitle: scenario_subtitle ?? '',
    scenario_category: scenario_category ?? 'attack-scenario',
    scenario_main_focus: scenario_main_focus ?? 'incident-response',
    scenario_severity: scenario_severity ?? 'high',
    scenario_description: scenario_description ?? '',
    scenario_tags: scenario_tags ?? [],
    scenario_external_reference: scenario_external_reference ?? '',
    scenario_external_url: scenario_external_url ?? '',
    scenario_message_header: scenario_message_header ?? '',
    scenario_message_footer: scenario_message_footer ?? '',
    scenario_mail_from: scenario_mail_from ?? '',
    scenario_mails_reply_to: scenario_mails_reply_to ?? [],
    scenario_custom_dashboard: scenario_custom_dashboard ?? '',
  }))(scenario);

  const [scenarioFormData, setScenarioFormData] = useState<UpdateScenarioInput>(initialValues);

  const submitScenarioUpdate = (data: UpdateScenarioInput) => {
    dispatch(updateScenario(scenario.scenario_id, data));
    handleClose();
  };

  const submitEdit = (data: UpdateScenarioInput) => {
    setScenarioFormData(data);

    // before updating the scenario we are checking if tag rules could apply
    // -> if yes we ask the user to apply or not apply the rules at the update
    checkScenarioTagRules(scenario.scenario_id, data.scenario_tags ?? []).then(
      (result: { data: CheckScenarioRulesOutput }) => {
        if (result.data.rules_found) {
          handleOpenApplyRule();
        } else {
          submitScenarioUpdate(data);
        }
      },
    );
  };

  const handleTagRuleChoice = (shouldApply: boolean) => {
    scenarioFormData.apply_tag_rule = shouldApply;
    submitScenarioUpdate(scenarioFormData);
    handleCloseApplyRule();
  };

  return (
    <>
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Update the scenario')}
      >
        <ScenarioForm
          initialValues={initialValues}
          editing
          disabled={permissions.readOnly}
          onSubmit={submitEdit}
          handleClose={handleClose}
        />
      </Drawer>
      <DialogApplyTagRule
        open={openApplyRule}
        handleClose={handleCloseApplyRule}
        handleApplyRule={() => handleTagRuleChoice(true)}
        handleDontApplyRule={() => handleTagRuleChoice(false)}
      />
    </>
  );
};

export default ScenarioUpdate;
