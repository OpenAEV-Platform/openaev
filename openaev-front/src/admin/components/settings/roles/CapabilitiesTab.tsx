import { LocalPoliceOutlined, LockOutlined } from '@mui/icons-material';
import { Box, Checkbox, Divider, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Controller, type FieldValues, type Path, useFormContext, useWatch } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type CapabilityOutput } from '../../../../utils/api-types';
import useCapabilityGrants from '../../../../utils/hooks/useCapabilityGrants';

interface CapabilitiesTabProps<T extends FieldValues> {
  capabilities: CapabilityOutput[];
  capability: CapabilityOutput;
  fieldName: Path<T>;
  depth?: number;
}

function CapabilitiesTab<T extends FieldValues>({ capabilities, capability, fieldName, depth = 0 }: CapabilitiesTabProps<T>) {
  const { t } = useFormatter();
  const theme = useTheme();
  const { holdsCapability } = useCapabilityGrants(capabilities);

  const { classes } = makeStyles()(() => ({
    capability_name: {
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(0.5),
      margin: theme.spacing(1),
    },
  }))();

  const { control } = useFormContext<T>();
  const selected = (useWatch({
    control,
    name: fieldName,
  }) ?? []) as string[];

  const canGrantCapability = (cap: CapabilityOutput): boolean =>
    !cap.capability_checkable || holdsCapability(cap.capability_value);

  // Get all children's capabilities
  const getAllChildren = (cap: CapabilityOutput): string[] => {
    const children: string[] = [];

    const collectCheckableValues = (c: CapabilityOutput) => {
      if (c.capability_checkable && c.capability_value) {
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
        if (directChild && cap.capability_checkable && cap.capability_value) {
          return [...parents, cap.capability_value];
        }

        const foundParents = getAllParents(targetValue, cap.capability_children,
          cap.capability_checkable && cap.capability_value ? [...parents, cap.capability_value] : parents);
        if (foundParents.length > (cap.capability_checkable && cap.capability_value ? parents.length + 1 : parents.length)) {
          return foundParents;
        }
      }
    }
    return parents;
  };

  const toggle = (checked: boolean, cap: CapabilityOutput, allCapabilities: CapabilityOutput[]) => {
    if (checked && !canGrantCapability(cap)) {
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

  const hasGrantableDescendant = (cap: CapabilityOutput): boolean =>
    (cap.capability_children ?? []).some(child =>
      (child.capability_checkable ? canGrantCapability(child) : hasGrantableDescendant(child)));

  const isSelected = capability.capability_value ? selected.includes(capability.capability_value) : false;
  // The API validates the resulting set, so an unheld capability can be revoked but never granted.
  const isCapabilityRestricted = capability.capability_checkable
    ? !canGrantCapability(capability)
    : (capability.capability_children?.length ?? 0) > 0 && !hasGrantableDescendant(capability);
  const isCapabilityDisabled = isCapabilityRestricted && !isSelected;

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
          opacity: isCapabilityRestricted ? 0.5 : 1,
        }}
      >
        <Box
          className={classes.capability_name}
          sx={{ color: isCapabilityRestricted ? 'text.disabled' : 'inherit' }}
        >
          <LocalPoliceOutlined sx={{ opacity: capability.capability_checkable ? 1 : 0.5 }} />
          {t(capability.capability_value)}
          {isCapabilityRestricted && (
            <Tooltip title={t('The current user does not have this capability: it can only be removed, not granted')}>
              <LockOutlined
                sx={{
                  ml: theme.spacing(0.5),
                  fontSize: theme.typography.body1.fontSize,
                  color: 'text.disabled',
                }}
              />
            </Tooltip>
          )}
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
                  disabled={isCapabilityDisabled}
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
