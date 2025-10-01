import { SecurityOutlined } from '@mui/icons-material';
import { Box, Button, Checkbox, Divider } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FC, useEffect, useState } from 'react';

import { fetchRoles } from '../../../../actions/roles/roles-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type RoleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

interface GroupManageRolesProps {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (roleIds: string[]) => void;
}

const GroupManageRoles: FC<GroupManageRolesProps> = (
  {
    initialState,
    open,
    onClose,
    onSubmit,
  },
) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();

  const [roles, setRoles] = useState<RoleOutput[]>([]);
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>(initialState);

  const handleToggleRole = (roleId: string) => {
    setSelectedRoleIds(prev =>
      prev.includes(roleId)
        ? prev.filter(id => id !== roleId)
        : [...prev, roleId],
    );
  };

  const fetchRolesToLoad = () => {
    return dispatch(fetchRoles())
      .then((result: { entities: { roles: Record<string, RoleOutput> } }) => {
        const rolesArray = Object.values(result.entities.roles);
        setRoles(rolesArray);
      });
  };

  useEffect(() => {
    if (open) {
      fetchRolesToLoad();
    }
  }, [open]);

  const handleClose = () => {
    setRoles([]);
    onClose();
  };

  const handleSubmit = () => {
    onSubmit(selectedRoleIds);
    handleClose();
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Manage the roles of this group')}
    >
      <Box>
        {roles.map(role => (
          <div key={role.role_id}>
            <Box display="flex" alignItems="center" gap={theme.spacing(2)} justifyContent="space-between">
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(1),
              }}
              >
                <SecurityOutlined />
                <p>{role.role_name}</p>
              </div>
              <Checkbox
                checked={selectedRoleIds.includes(role.role_id)}
                onChange={() => handleToggleRole(role.role_id)}
              />
            </Box>
            <Divider />
          </div>
        ))}

        <div style={{
          float: 'right',
          marginTop: theme.spacing(2),
        }}
        >
          <Button variant="contained" style={{ marginRight: theme.spacing(1) }} onClick={onClose}>
            {t('Cancel')}
          </Button>
          <Button variant="contained" color="secondary" onClick={handleSubmit}>
            {t('Update')}
          </Button>
        </div>
      </Box>
    </Drawer>
  );
};

export default GroupManageRoles;
