import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

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
      <Tooltip>
        <TooltipTrigger asChild>
          <span>
            {privileges.adminCount > 0 && (<AgentPrivilege variant="list" privilege="admin" />)}
          </span>
        </TooltipTrigger>
        {(t('Admin') + `: ${privileges.adminCount}`) && <TooltipContent side="top">{t('Admin') + `: ${privileges.adminCount}`}</TooltipContent>}
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <span>
            {privileges.userCount > 0 && (<AgentPrivilege variant="list" privilege="user" />)}
          </span>
        </TooltipTrigger>
        {(t('User') + `: ${privileges.userCount}`) && <TooltipContent side="top">{t('User') + `: ${privileges.userCount}`}</TooltipContent>}
      </Tooltip>
      {
        props.privileges && props.privileges.length === 0 && (
          <span>-</span>
        )
      }
    </>
  );
};

export default EndpointAgentsPrivilegeFragment;
