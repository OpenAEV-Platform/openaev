import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Button, CircularProgress } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import FileFieldController from '../../../../components/fields/FileFieldController';
import ScenarioField from '../../../../components/fields/ScenarioField';
import SimulationField from '../../../../components/fields/SimulationField';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type DocumentCreateInput } from '../../../../utils/api-types';

// The file is only part of the creation form: it is uploaded as multipart
// alongside the JSON input, it is not a field of DocumentCreateInput.
export type DocumentFormInput = DocumentCreateInput & { document_file?: File[] };

interface Props {
  onSubmit: SubmitHandler<DocumentFormInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<DocumentFormInput>;
  filters?: string[];
}

const DocumentForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing = false,
  initialValues = {},
  filters,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = z.object({
    document_description: z.string().optional(),
    document_exercises: z.array(z.string()).optional(),
    document_scenarios: z.array(z.string()).optional(),
    document_tags: z.array(z.string()).optional(),
    document_file: z.array(z.instanceof(File)).optional(),
  }).refine(data => editing || (data.document_file?.length ?? 0) > 0, {
    message: t('This field is required.'),
    path: ['document_file'],
  });

  const methods = useForm<DocumentFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: {
      document_description: '',
      document_exercises: [],
      document_scenarios: [],
      document_tags: [],
      ...initialValues,
    },
  });

  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = methods;

  return (
    <FormProvider {...methods}>
      <form
        id="documentForm"
        onSubmit={handleSubmit(onSubmit)}
      >
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        >
          <TextFieldController
            variant="standard"
            name="document_description"
            multiline
            rows={2}
            label={t('Description')}
          />
          <SimulationField
            multiple
            useForm
            name="document_exercises"
            label={t('Simulations')}
          />
          <ScenarioField
            multiple
            useForm
            name="document_scenarios"
            label={t('Scenarios')}
          />
          <TagFieldController
            name="document_tags"
            label={t('Tags')}
          />
          {!editing && (
            <FileFieldController
              name="document_file"
              label={t('Select a file')}
              filters={filters}
            />
          )}
        </Box>
        <div style={{
          display: 'flex',
          float: 'right',
          gap: theme.spacing(1),
          marginTop: theme.spacing(1),
        }}
        >
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={!isDirty || isSubmitting}
            startIcon={isSubmitting && <CircularProgress size={20} />}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default DocumentForm;
