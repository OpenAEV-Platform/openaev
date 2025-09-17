import { LocalPoliceOutlined } from '@mui/icons-material';
import { Box, Checkbox, Divider } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FC } from 'react';
import { Controller, useFormContext, useWatch } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type RoleCreateInput } from './RoleForm';

interface Capability {
  name: string;
  value: string;
  checkable: boolean;
  children?: Capability[];
}

interface CapabilitiesTabProps {
  capabilities: Capability[];
  capability: Capability;
  depth?: number;
}

const CapabilitiesTab: FC<CapabilitiesTabProps> = ({ capabilities, capability, depth = 0 }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const { classes } = makeStyles()(() => ({
    capability_name: {
      display: 'flex',
      alignItems: 'center',
      gap: 4,
      margin: theme.spacing(1),
    },
  }))();

  const { control } = useFormContext<RoleCreateInput>();
  const selected = useWatch({
    control,
    name: 'role_capabilities',
  }) ?? [];

  // Get all children's capabilities
  const getAllChildren = (capability: Capability): string[] => {
    const children: string[] = [];

    const collectCheckableValues = (cap: Capability) => {
      if (cap.checkable && cap.value) {
        children.push(cap.value);
      }
      cap.children?.forEach(child => collectCheckableValues(child));
    };

    capability.children?.forEach(child => collectCheckableValues(child));
    return children;
  };

  // Get all parent's capabilities
  const getAllParents = (targetValue: string, capabilities: Capability[], parents: string[] = []): string[] => {
    for (const capability of capabilities) {
      if (capability.children) {
        const directChild = capability.children.find(child => child.value === targetValue);
        if (directChild && capability.checkable && capability.value) {
          return [...parents, capability.value];
        }

        const foundParents = getAllParents(targetValue, capability.children,
          capability.checkable && capability.value ? [...parents, capability.value] : parents);
        if (foundParents.length > (capability.checkable && capability.value ? parents.length + 1 : parents.length)) {
          return foundParents;
        }
      }
    }
    return parents;
  };

  const toggle = (checked: boolean, capability: Capability, allCapabilities: Capability[]) => {
    let newSelected = [...selected];

    if (checked) {
      // check item
      if (!newSelected.includes(capability.value)) {
        newSelected.push(capability.value);
      }

      // Check his parents
      const parents = getAllParents(capability.value, allCapabilities);
      parents.forEach((parentValue) => {
        if (!newSelected.includes(parentValue)) {
          newSelected.push(parentValue);
        }
      });
    } else {
      // uncheck item
      newSelected = newSelected.filter(v => v !== capability.value);

      // uncheck his children
      const children = getAllChildren(capability);
      newSelected = newSelected.filter(v => !children.includes(v));
    }

    return newSelected;
  };

  return (
    <>
      <Box
        pl={depth * 2}
        display="flex"
        alignItems="center"
        justifyContent="space-between"
        width="100%"
        sx={{
          backgroundColor: selected.includes(capability.value)
            ? 'action.selected'
            : 'transparent',
          paddingRight: theme.spacing(2),
        }}
      >
        <div className={classes.capability_name}>
          <LocalPoliceOutlined sx={{ opacity: capability.checkable ? 1 : 0.5 }} />
          <>
            {t(capability.name)}
          </>
        </div>
        {capability.checkable && capability.value
          && (
            <Controller
              name="role_capabilities"
              control={control}
              render={({ field }) => (
                <Checkbox
                  sx={{
                    m: 0,
                    p: 0,
                  }}
                  checked={selected.includes(capability.value)}
                  onChange={e => field.onChange(toggle(e.target.checked, capability, capabilities))}
                />
              )}
            />
          )}

      </Box>
      <Divider />

      {capability.children?.map(child => (
        <CapabilitiesTab
          key={child.name}
          capability={child}
          depth={depth + 2}
          capabilities={capabilities}
        />
      ))}
    </>
  );
};

export default CapabilitiesTab;
