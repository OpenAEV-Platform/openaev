import { Add, DeleteOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useFieldArray, useFormContext } from 'react-hook-form';

import SelectFieldController, { createItems, type Item } from '../../../../../components/fields/SelectFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import type { CustomDashboardInput, CustomDashboardParametersInput } from '../../../../../utils/api-types';

const ParametersTab = () => {
  const { t } = useFormatter();
  const theme = useTheme();

  const { control } = useFormContext<CustomDashboardInput>();
  const { fields, append, remove } = useFieldArray({
    control,
    name: 'custom_dashboard_parameters',
  });

  const items: Item<CustomDashboardParametersInput['custom_dashboards_parameter_type']>[] = createItems(['scenario', 'simulation']);
  const handleAddParameter = (type: CustomDashboardParametersInput['custom_dashboards_parameter_type']) => {
    if (type) {
      append({
        custom_dashboards_parameter_name: type,
        custom_dashboards_parameter_type: type,
      });
    }
  };

  return (
    <>
      <div style={{
        display: 'flex',
        alignItems: 'center',
      }}
      >
        <Typography variant="h3" sx={{ m: 0 }}>
          {t('Parameters')}
        </Typography>
        <IconButton
          color="secondary"
          aria-label="Add"
          onClick={() => handleAddParameter(items[0].value)}
          size="small"
        >
          <Add fontSize="small" />
        </IconButton>
      </div>
      {fields
        .map((field, index) => (
          (field.custom_dashboards_parameter_type === 'simulation' || field.custom_dashboards_parameter_type === 'scenario') && (
            <Box
              key={field.id}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(2),
              }}
            >
              <>
                <TextFieldController
                  name={`custom_dashboard_parameters.${index}.custom_dashboards_parameter_name`}
                  label={t('Parameter Name')}
                  variant="standard"
                  required
                  noHelperText
                />
                <SelectFieldController
                  name={`custom_dashboard_parameters.${index}.custom_dashboards_parameter_type`}
                  label={t('Parameter Type')}
                  items={items}
                  required
                />
                <Tooltip title={t('Delete')}>
                  <IconButton color="error" onClick={() => remove(index)}>
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>

            </Box>
          )))}
    </>
  );
};

export default ParametersTab;
