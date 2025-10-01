import { zodResolver } from '@hookform/resolvers/zod';
import { Button, MenuItem, TextField } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { Controller, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import FileLoader from '../../../../components/fields/FileLoader';
import TagField from '../../../../components/fields/TagField';
import { useFormatter } from '../../../../components/i18n';
import { type SecurityPlatformInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<SecurityPlatformInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: SecurityPlatformInput;
  securityPlatformId?: string;
}

const SecurityPlatformForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing,
  initialValues = {
    asset_name: '',
    security_platform_type: 'SIEM',
    asset_description: '',
    security_platform_logo_light: undefined,
    security_platform_logo_dark: undefined,
    asset_tags: [],
  },
  securityPlatformId,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<SecurityPlatformInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<SecurityPlatformInput>().with({
        asset_name: z.string().min(1, { message: t('Should not be empty') }),
        security_platform_type: z.enum(['EDR', 'XDR', 'SIEM', 'SOAR', 'NDR', 'ISPM']),
        asset_description: z.string().optional(),
        security_platform_logo_light: z.string().optional(),
        security_platform_logo_dark: z.string().optional(),
        asset_tags: z.string().array().optional(),
        asset_external_reference: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  return (
    <form id="securityPlatformForm" onSubmit={handleSubmitWithoutPropagation}>
      <TextField
        variant="standard"
        fullWidth
        label={t('Name')}
        style={{ marginTop: 10 }}
        error={!!errors.asset_name}
        helperText={errors.asset_name?.message}
        inputProps={register('asset_name')}
        InputLabelProps={{ required: true }}
      />
      <Controller
        control={control}
        name="security_platform_type"
        render={({ field }) => (
          <TextField
            select
            variant="standard"
            fullWidth
            value={field.value}
            label={t('Platform')}
            style={{ marginTop: 20 }}
            error={!!errors.security_platform_type}
            helperText={errors.security_platform_type?.message}
            inputProps={register('security_platform_type')}
            InputLabelProps={{ required: true }}
          >
            <MenuItem value="EDR">{t('EDR')}</MenuItem>
            <MenuItem value="XDR">{t('XDR')}</MenuItem>
            <MenuItem value="SIEM">{t('SIEM')}</MenuItem>
            <MenuItem value="SOAR">{t('SOAR')}</MenuItem>
            <MenuItem value="NDR">{t('NDR')}</MenuItem>
            <MenuItem value="ISPM">{t('ISPM')}</MenuItem>
          </TextField>
        )}
      />
      <TextField
        variant="standard"
        fullWidth
        multiline
        rows={2}
        label={t('Description')}
        style={{ marginTop: 20 }}
        error={!!errors.asset_description}
        helperText={errors.asset_description?.message}
        inputProps={register('asset_description')}
      />
      <Controller
        control={control}
        name="security_platform_logo_light"
        render={({ field: { onChange, value } }) => (
          <FileLoader
            name="security_platform_logo_light"
            label={t('Logo light')}
            extensions={['png', 'jpg', 'jpeg', 'svg', 'gif']}
            setFieldValue={(_name, document) => {
              onChange(document?.id);
            }}
            initialValue={{ id: value }}
            parentResourceType="security_platform"
            parentResourceId={securityPlatformId}
          />
        )}
      />
      <Controller
        control={control}
        name="security_platform_logo_dark"
        render={({ field: { onChange, value } }) => (
          <FileLoader
            name="security_platform_logo_dark"
            label={t('Logo dark')}
            extensions={['png', 'jpg', 'tiff', 'tif', 'bmp', 'jpeg', 'svg', 'gif']}
            setFieldValue={(_name, document) => {
              onChange(document?.id);
            }}
            initialValue={{ id: value }}
            parentResourceType="security_platform"
            parentResourceId={securityPlatformId}
          />
        )}
      />
      <Controller
        control={control}
        name="asset_tags"
        render={({ field: { onChange, value }, fieldState: { error } }) => (
          <TagField
            label={t('Tags')}
            fieldValue={value ?? []}
            fieldOnChange={onChange}
            error={error}
            style={{ marginTop: 20 }}
          />
        )}
      />
      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button
          variant="contained"
          onClick={handleClose}
          style={{ marginRight: 10 }}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="secondary"
          type="submit"
          disabled={!isDirty || isSubmitting}
        >
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default SecurityPlatformForm;
