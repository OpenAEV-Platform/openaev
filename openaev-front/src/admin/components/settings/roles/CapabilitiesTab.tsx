import { LocalPoliceOutlined } from '@mui/icons-material';
import { Box, Checkbox, Divider } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Controller, type FieldValues, type Path, useFormContext, useWatch } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

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

  const { classes } = makeStyles()(() => ({
    capability_name: {
      display: 'flex',
      alignItems: 'center',
      gap: 4,
      margin: theme.spacing(1),
    },
  }))();

  const { control } = useFormContext<T>();
  const selected = (useWatch({
    control,
    name: fieldName,
  }) ?? []) as string[];

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

  return (
    <>
      <Box
        pl={depth * 2}
        display="flex"
        alignItems="center"
        justifyContent="space-between"
        width="100%"
        sx={{
          backgroundColor: selected.includes(capability.capability_value)
            ? 'action.selected'
            : 'transparent',
          paddingRight: theme.spacing(2),
        }}
      >
        <div className={classes.capability_name}>
          <LocalPoliceOutlined sx={{ opacity: capability.capability_checkable ? 1 : 0.5 }} />
          {t(capability.capability_value)}
        </div>
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
                  checked={selected.includes(capability.capability_value)}
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
