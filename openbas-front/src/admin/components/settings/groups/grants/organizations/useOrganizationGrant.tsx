import { Checkbox } from '@mui/material';
import { useEffect } from 'react';

import { addGroupOrganization, deleteGroupOrganization } from '../../../../../../actions/Grant';
import { fetchGroup } from '../../../../../../actions/Group';
import { type GroupHelper } from '../../../../../../actions/group/group-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type Organization } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import { type TableConfig } from '../ui/TableData';

const useOrganizationGrant = (groupId: string) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const group = useHelper((helper: GroupHelper) => helper.getGroup(groupId));

  useEffect(() => {
    dispatch(fetchGroup(groupId));
  }, [dispatch]);

  if (!group) {
    return { configs: [] };
  }

  const handleGrant = (organizationId: string, checked: boolean) => {
    if (checked) {
      dispatch(addGroupOrganization(group.group_id, { organization_id: organizationId }));
    } else {
      dispatch(deleteGroupOrganization(group.group_id, organizationId));
    }
  };

  const configs: TableConfig<Organization>[] = [
    {
      label: t('Organization'),
      value: organization => organization.organization_name,
      width: '40%',
      align: 'left',
    },
    {
      label: t('Granted'),
      value: (organization) => {
        const checked = (group.group_organizations ?? []).includes(organization.organization_id);
        return (
          <Checkbox
            checked={checked}
            onChange={(_, c) => handleGrant(organization.organization_id, c)}
          />
        );
      },
      width: '60%',
    },
  ];

  return { configs };
};

export default useOrganizationGrant;
