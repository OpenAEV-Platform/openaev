import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import type { ChallengeTryInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<ChallengeTryInput>;
  handleClose: () => void;
}

const ChallengeTryForm: FunctionComponent<Props> = ({ handleClose, onSubmit }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<ChallengeTryInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ChallengeTryInput>().with({ challenge_value: z.string().min(1, { message: t('This field is required.') }) }),
    ),
  });

  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = methods;

  return (
    <FormProvider {...methods}>
      <form
        id="challengeForm"
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
        onSubmit={handleSubmit(onSubmit)}
      >
        <TextFieldController
          variant="standard"
          required
          name="challenge_value"
          label={t('Flag')}
        />
        <div style={{ alignSelf: 'flex-end' }}>
          <Button
            onClick={handleClose}
            style={{ marginRight: theme.spacing(1) }}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            color="secondary"
            type="submit"
            disabled={isSubmitting || !isDirty}
          >
            {t('Submit')}
          </Button>
        </div>
      </form>
    </FormProvider>

  );
};

export default ChallengeTryForm;
