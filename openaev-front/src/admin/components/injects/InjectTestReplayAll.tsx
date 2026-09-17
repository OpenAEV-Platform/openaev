import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ForwardToInbox } from '@mui/icons-material';
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
          <Tooltip>
            <TooltipTrigger asChild>
              <span>
                <span className="inline-flex">
                  <IconButton
                    icon={<ForwardToInbox fontSize="small" />}
                    aria-label="test"
                    disabled={
                      injectIds?.length === 0
                    }
                    onClick={handleOpenAllTest}
                    priority="tertiary"
                    size="sm"
                  />
                </span>
              </span>
            </TooltipTrigger>
            <TooltipContent>{t('Replay all tests')}</TooltipContent>
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
