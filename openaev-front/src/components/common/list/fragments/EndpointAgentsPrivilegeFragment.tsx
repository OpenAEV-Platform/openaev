import { Tooltip } from '@mui/material';

import AgentPrivilege from '../../../../admin/components/assets/endpoints/AgentPrivilege';
import { useFormatter } from '../../../i18n';

type Props = { privileges?: (string | undefined)[] };

const EndpointAgentsPrivilegeFragment = (props: Props) => {
  const { t } = useFormatter();

  const getPrivilegesCount = (privileges: (string | undefined)[]) => {
    if (privileges.length > 0) {
      const privilegeCount = privileges?.reduce((count, privilege) => {
        if (privilege === 'admin') {
          count.admin += 1;
        } else {
          count.user += 1;
        }
        return count;
      }, {
        admin: 0,
        user: 0,
      });

      return {
        adminCount: privilegeCount?.admin,
        userCount: privilegeCount?.user,
      };
    } else {
      return {
        adminCount: 0,
        userCount: 0,
      };
    }
  };

  const privileges = getPrivilegesCount(props.privileges ?? []);

  return (
    <>
      <Tooltip title={t('Admin') + `: ${privileges.adminCount}`} placement="top">
        <span>
          {privileges.adminCount > 0 && (<AgentPrivilege variant="list" privilege="admin" />)}
        </span>
      </Tooltip>
      <Tooltip title={t('User') + `: ${privileges.userCount}`} placement="top">
        <span>
          {privileges.userCount > 0 && (<AgentPrivilege variant="list" privilege="user" />)}
        </span>
      </Tooltip>
      {
        props.privileges && props.privileges.length === 0 && (
          <span>{t('N/A')}</span>
        )
      }
    </>
  );
};

export default EndpointAgentsPrivilegeFragment;
