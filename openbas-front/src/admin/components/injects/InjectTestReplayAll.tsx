import { ForwardToInbox } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';

import DialogTest from '../../../components/common/DialogTest';
import { useFormatter } from '../../../components/i18n';
import { type InjectTestStatusOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { InjectTestContext, PermissionsContext } from '../common/Context';

interface Props {
  searchPaginationInput: SearchPaginationInput;
  injectIds: string[] | undefined;
  onTest?: (result: InjectTestStatusOutput[]) => void;
}

const InjectTestReplayAll: FunctionComponent<Props> = ({
  searchPaginationInput,
  injectIds,
  onTest,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const [openAllTest, setOpenAllTest] = useState(false);

  const {
    contextId,
    bulkTestInjects,
  } = useContext(InjectTestContext);

  const handleOpenAllTest = () => {
    setOpenAllTest(true);
  };

  const handleCloseAllTest = () => {
    setOpenAllTest(false);
  };

  const handleSubmitAllTest = () => {
    if (bulkTestInjects) {
      bulkTestInjects(contextId, {
        search_pagination_input: searchPaginationInput,
        simulation_or_scenario_id: contextId,
      }!).then((result: { data: InjectTestStatusOutput[] }) => {
        onTest?.(result.data);
        MESSAGING$.notifySuccess(t('Test(s) sent'));
        return result;
      });
    }
    handleCloseAllTest();
  };

  return (
    <>
      {permissions.canLaunch
        && (
          <Tooltip title={t('Replay all tests')}>
            <span>
              <IconButton
                aria-label="test"
                disabled={
                  injectIds?.length === 0
                }
                onClick={handleOpenAllTest}
                color="primary"
                size="small"
              >
                <ForwardToInbox fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
        )}
      <DialogTest
        open={openAllTest}
        handleClose={handleCloseAllTest}
        handleSubmit={handleSubmitAllTest}
        text={t('Do you want to replay all these tests?')}
      />
    </>

  );
};

export default InjectTestReplayAll;
