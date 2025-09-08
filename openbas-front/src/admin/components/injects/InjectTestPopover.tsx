import { type FunctionComponent, useContext, useState } from 'react';

import ButtonPopover from '../../../components/common/ButtonPopover';
import DialogDelete from '../../../components/common/DialogDelete';
import DialogTest from '../../../components/common/DialogTest';
import { useFormatter } from '../../../components/i18n';
import { type InjectTestStatusOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { InjectTestContext, PermissionsContext } from '../common/Context';

interface Props {
  injectTest: InjectTestStatusOutput;
  onTest?: (result: InjectTestStatusOutput) => void;
  onDelete?: (result: string) => void;
}

const InjectTestPopover: FunctionComponent<Props> = ({
  injectTest,
  onDelete,
  onTest,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const [openDelete, setOpenDelete] = useState(false);
  const [openTest, setOpenTest] = useState(false);

  const {
    contextId,
    deleteInjectTest,
    testInject,
  } = useContext(InjectTestContext);

  const handleOpenDelete = () => {
    setOpenDelete(true);
  };
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    if (deleteInjectTest) {
      deleteInjectTest(contextId, injectTest.status_id);
      if (onDelete) {
        onDelete(injectTest.status_id!);
      }
    }
    handleCloseDelete();
  };

  const handleOpenTest = () => {
    setOpenTest(true);
  };

  const handleCloseTest = () => {
    setOpenTest(false);
  };

  const submitTest = () => {
    if (testInject) {
      testInject(contextId, injectTest.inject_id!).then((result: { data: InjectTestStatusOutput }) => {
        onTest?.(result.data);
        MESSAGING$.notifySuccess(t(`Test for inject ${injectTest.inject_title} has been sent`));
        return result;
      });
    }
    handleCloseTest();
  };

  // Button Popover
  const entries = [{
    label: t('Replay test'),
    action: () => handleOpenTest(),
    userRight: permissions.canLaunch,
  }, {
    label: t('Delete test'),
    action: () => handleOpenDelete(),
    userRight: permissions.canManage,
  }];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this test?')}
      />
      <DialogTest
        open={openTest}
        handleClose={handleCloseTest}
        handleSubmit={submitTest}
        text={t('Do you want to replay this test?')}
      />
    </>
  );
};

export default InjectTestPopover;
