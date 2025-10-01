import { type FunctionComponent } from 'react';

import { createNotificationRule, deleteNotificationRule, updateNotificationRule } from '../../../../../actions/scenarios/scenario-notification-rules';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type CreateNotificationRuleInput, type NotificationRuleOutput, type UpdateNotificationRuleInput } from '../../../../../utils/api-types';
import NotificationRuleForm from './NotificationRuleForm';

interface Props {
  open: boolean;
  editing: boolean;
  setOpen: (open: boolean) => void;
  scenarioName: string;
  scenarioId: string;
  notificationRule: NotificationRuleOutput;
  onCreate?: (result: NotificationRuleOutput) => void;
  onUpdate?: (result: NotificationRuleOutput) => void;
  onDelete?: () => void;
}

const ScenarioNotificationRulesDrawer: FunctionComponent<Props> = ({
  open,
  editing,
  setOpen,
  scenarioName,
  scenarioId,
  notificationRule,
  onCreate,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();

  const creationInitialValues = ({
    resource_id: '',
    resource_type: 'SCENARIO',
    trigger: 'DIFFERENCE',
    type: 'EMAIL',
    subject: { scenarioName }.scenarioName + ' - alert',
  });

  const onSubmitCreation = async (data: CreateNotificationRuleInput) => {
    const toCreate = {
      ...data,
      resource_id: scenarioId,
    };
    await createNotificationRule(toCreate).then((result: { data: NotificationRuleOutput }) => {
      setOpen(false);
      if (onCreate) {
        onCreate(result.data);
      }
    });
  };

  const editionInitialValues = (({ notification_rule_subject }) => ({
    resource_id: '',
    resource_type: 'SCENARIO',
    trigger: 'DIFFERENCE',
    type: 'EMAIL',
    subject: notification_rule_subject ?? '',
  }))(notificationRule);

  const onSubmitEdition = async (data: CreateNotificationRuleInput) => {
    const toUpdate: UpdateNotificationRuleInput = { subject: data.subject };
    await updateNotificationRule(notificationRule.notification_rule_id, toUpdate).then((result: { data: NotificationRuleOutput }) => {
      if (result) {
        if (onUpdate) {
          onUpdate(result.data);
        }
        setOpen(false);
      }
      return result.data;
    });
  };

  const onDeleteNotificationRule = () => {
    deleteNotificationRule(notificationRule.notification_rule_id).then(() => {
      setOpen(false);
      if (onDelete) {
        onDelete();
      }
    });
  };

  return (
    <Drawer
      open={open}
      handleClose={() => setOpen(false)}
      title={editing ? t('Update a notification rule') : t('Create a notification rule')}
    >
      {
        editing
          ? (

              <NotificationRuleForm
                edition={editing}
                onSubmit={onSubmitEdition}
                onDelete={onDeleteNotificationRule}
                initialValues={editionInitialValues}
              />

            )
          : (
              <NotificationRuleForm
                edition={editing}
                onSubmit={onSubmitCreation}
                handleClose={() => setOpen(false)}
                onDelete={onDeleteNotificationRule}
                initialValues={creationInitialValues}
              />
            )
      }

    </Drawer>
  );
};

export default ScenarioNotificationRulesDrawer;
