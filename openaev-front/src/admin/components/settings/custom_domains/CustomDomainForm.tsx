import { zodResolver } from '@hookform/resolvers/zod';
import { Button, TextField } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import { useFormatter } from '../../../../components/i18n';
import { type CustomDomainInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

interface Props { onSubmit: SubmitHandler<CustomDomainInput> }

// A permissive-but-sane FQDN check: labels of letters/digits/hyphens separated by dots, at least one
// dot, no scheme or path. The authoritative normalisation and uniqueness check happen server-side.
const HOSTNAME_REGEX = /^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\.(?!-)[a-z0-9-]{1,63}(?<!-))+$/i;

const CustomDomainForm: FunctionComponent<Props> = ({ onSubmit }) => {
  const { t } = useFormatter();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CustomDomainInput>({
    mode: 'onChange',
    resolver: zodResolver(
      zodImplement<CustomDomainInput>().with({
        custom_domain_hostname: z
          .string()
          .min(1, { message: t('Should not be empty') })
          .regex(HOSTNAME_REGEX, { message: t('Enter a valid domain, e.g. security.acme.com') }),
      }),
    ),
    defaultValues: { custom_domain_hostname: '' },
  });

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  return (
    <form id="customDomainForm" onSubmit={handleSubmitWithoutPropagation}>
      <TextField
        variant="standard"
        fullWidth
        label={t('Domain')}
        placeholder="security.acme.com"
        error={!!errors.custom_domain_hostname}
        helperText={
          errors.custom_domain_hostname?.message
          ?? t('The hostname your recipients will see, e.g. security.acme.com')
        }
        inputProps={{ 'data-testid': 'custom-domain-hostname' }}
        {...register('custom_domain_hostname')}
      />
      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button
          variant="contained"
          color="primary"
          type="submit"
          disabled={isSubmitting}
        >
          {t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default CustomDomainForm;
