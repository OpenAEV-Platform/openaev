import { LocalPoliceOutlined } from '@mui/icons-material';
import { Box, Checkbox, Divider } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Controller, type FieldValues, type Path, useFormContext, useWatch } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import { type CapabilityOutput } from '../../../../utils/api-types';

interface CapabilitiesTabProps<T extends FieldValues> {
  capabilities: CapabilityOutput[];
  capability: CapabilityOutput;
  fieldName: Path<T>;
  depth?: number;
}

function CapabilitiesTab<T extends FieldValues>({ capabilities, capability, fieldName, depth = 0 }: CapabilitiesTabProps<T>) {
  const { t } = useFormatter();
  const theme = useTheme();

  const { control } = useFormContext<T>();
  const selected = (useWatch({
    control,
    name: fieldName,
  }) ?? []) as string[];

  const canAssignCapability = (cap: CapabilityOutput): boolean => cap.capability_user_can_have !== false;

  // Get all children's capabilities
  const getAllChildren = (cap: CapabilityOutput): string[] => {
    const children: string[] = [];

    const collectCheckableValues = (c: CapabilityOutput) => {
      if (c.capability_checkable && c.capability_value && canAssignCapability(c)) {
        children.push(c.capability_value);
      }
      c.capability_children?.forEach(child => collectCheckableValues(child));
    };

    cap.capability_children?.forEach(child => collectCheckableValues(child));
    return children;
  };

  // Get all parent's capabilities
  const getAllParents = (targetValue: string, caps: CapabilityOutput[], parents: string[] = []): string[] => {
    for (const cap of caps) {
      if (cap.capability_children) {
        const directChild = cap.capability_children.find(child => child.capability_value === targetValue);
        if (directChild && cap.capability_checkable && cap.capability_value && canAssignCapability(cap)) {
          return [...parents, cap.capability_value];
        }

        const foundParents = getAllParents(targetValue, cap.capability_children,
          cap.capability_checkable && cap.capability_value && canAssignCapability(cap) ? [...parents, cap.capability_value] : parents);
        if (foundParents.length > (cap.capability_checkable && cap.capability_value && canAssignCapability(cap) ? parents.length + 1 : parents.length)) {
          return foundParents;
        }
      }
    }
    return parents;
  };

  const toggle = (checked: boolean, cap: CapabilityOutput, allCapabilities: CapabilityOutput[]) => {
    if (!cap.capability_value || !canAssignCapability(cap)) {
      return selected;
    }

    let newSelected = [...selected];

    if (checked) {
      if (!newSelected.includes(cap.capability_value)) {
        newSelected.push(cap.capability_value);
      }

      const parents = getAllParents(cap.capability_value, allCapabilities);
      parents.forEach((parentValue) => {
        if (!newSelected.includes(parentValue)) {
          newSelected.push(parentValue);
        }
      });
    } else {
      newSelected = newSelected.filter(v => v !== cap.capability_value);

      const children = getAllChildren(cap);
      newSelected = newSelected.filter(v => !children.includes(v));
    }

    return newSelected;
  };

  const isAssignable = canAssignCapability(capability);
  const isCapabilityDisabled = capability.capability_checkable && !isAssignable;
  const isSelected = capability.capability_value ? selected.includes(capability.capability_value) : false;

  return (
    <>
      <Box
        pl={depth * 2}
        display="flex"
        alignItems="center"
        justifyContent="space-between"
        width="100%"
        sx={{
          backgroundColor: isSelected
            ? 'action.selected'
            : 'transparent',
          paddingRight: theme.spacing(2),
          opacity: isCapabilityDisabled ? 0.5 : 1,
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            margin: theme.spacing(1),
            color: isCapabilityDisabled ? theme.palette.text.disabled : undefined,
          }}
          title={isCapabilityDisabled ? t('You cannot assign this capability') : undefined}
        >
          <LocalPoliceOutlined sx={{ opacity: isCapabilityDisabled ? 0.5 : 1 }} />
          {t(capability.capability_value)}
        </Box>
        {capability.capability_checkable && capability.capability_value
          && (
            <Controller
              name={fieldName}
              control={control}
              render={({ field }) => (
                <Checkbox
                  sx={{
                    m: 0,
                    p: 0,
                  }}
                  checked={isSelected}
                  disabled={!isAssignable}
                  onChange={e => field.onChange(toggle(e.target.checked, capability, capabilities))}
                />
              )}
            />
          )}

      </Box>
      <Divider />

      {capability.capability_children?.map(child => (
        <CapabilitiesTab<T>
          key={child.capability_value}
          capability={child}
          fieldName={fieldName}
          depth={depth + 2}
          capabilities={capabilities}
        />
      ))}
    </>
  );
}

export default CapabilitiesTab;
