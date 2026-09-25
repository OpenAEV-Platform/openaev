import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo } from 'react';
import { FormProvider, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';

import Transition from '../../../components/common/Transition';
import SelectFieldController from '../../../components/fields/SelectFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import type { ScopeVariableInput } from '../../../utils/api-types';
import { formatPrimitiveTypeLabel } from '../../../utils/String';
import { zodImplement } from '../../../utils/Zod';
import { getPrimitiveFormatError } from './logic/primitive-types';
import usePrimitiveTypeDescriptors from './logic/usePrimitiveTypeDescriptors';

interface ScopeVariableCreateDialogProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: VariableFormValues) => void;
  existingVariables: Pick<ScopeVariableInput, 'scope_variable_key' | 'scope_variable_type'>[];
}

type VariableFormValues = Omit<ScopeVariableInput, 'scope_variable_id'>;

// The duplicate check spans both fields, so an edit on one of them can only be judged together
// with the other: they are revalidated as a pair.
const UNIQUENESS_FIELDS = ['scope_variable_key', 'scope_variable_type'] as const;

const ScopeVariableCreateDialog = ({
  open,
  onClose,
  onSubmit,
  existingVariables,
}: ScopeVariableCreateDialogProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  // A variable holds a single exact value, so the type's format applies unconditionally - unlike a
  // condition, where the operator decides. The backend enforces the same rule on save.
  const { descriptorsByType, primitiveTypes } = usePrimitiveTypeDescriptors();
  const typeItems = useMemo(
    () => primitiveTypes.map(type => ({
      value: type,
      label: t(formatPrimitiveTypeLabel(type)),
    })),
    [primitiveTypes, t],
  );

  const scopeVariableTypes = useMemo(
    () => primitiveTypes as [ScopeVariableInput['scope_variable_type'], ...ScopeVariableInput['scope_variable_type'][]],
    [primitiveTypes],
  );

  const existingVariablePairs = useMemo(
    () => new Set(
      existingVariables
        .map(variable => `${variable.scope_variable_key.trim()}::${variable.scope_variable_type}`),
    ),
    [existingVariables],
  );

  const schema = useMemo(
    () => zodImplement<VariableFormValues>()
      .with({
        scope_variable_key: z.string().min(1, { message: t('Key is required') }),
        scope_variable_type: z.enum(
          scopeVariableTypes.length > 0 ? scopeVariableTypes : ['text'],
          { message: t('Type is required') },
        ),
        scope_variable_value: z.string().min(1, { message: t('Value is required') }),
        scope_variable_description: z.string().optional(),
      })
      .superRefine((data, context) => {
        const formatError = getPrimitiveFormatError(
          data.scope_variable_type,
          data.scope_variable_value.trim(),
          descriptorsByType,
        );
        if (formatError) {
          context.addIssue({
            code: 'custom',
            path: ['scope_variable_value'],
            message: t(formatError),
          });
        }

        const normalizedKey = data.scope_variable_key.trim();
        const pairKey = `${normalizedKey}::${data.scope_variable_type}`;
        if (existingVariablePairs.has(pairKey)) {
          const message
            = 'A variable with this key and type already exists. Please change the key or the type.';
          context.addIssue({
            code: 'custom',
            path: ['scope_variable_key'],
            message: t(message),
          });
          context.addIssue({
            code: 'custom',
            path: ['scope_variable_type'],
            message: t(message),
          });
        }
      }),
    [descriptorsByType, existingVariablePairs, t, scopeVariableTypes],
  );

  const methods = useForm<VariableFormValues>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: {
      scope_variable_key: '',
      scope_variable_type: 'text',
      scope_variable_value: '',
      scope_variable_description: '',
    },
  });

  const {
    handleSubmit,
    reset,
    control,
    getFieldState,
    trigger,
    formState: { isDirty, isSubmitting },
  } = methods;

  const currentKey = useWatch({
    control,
    name: 'scope_variable_key',
  });
  const currentType = useWatch({
    control,
    name: 'scope_variable_type',
  });

  // React Hook Form only refreshes the error of the field being edited, so changing the key would
  // leave the duplicate error displayed on the type (and vice versa) even once the pair is unique.
  useEffect(() => {
    const erroredFields = UNIQUENESS_FIELDS.filter(field => getFieldState(field).error);
    if (erroredFields.length > 0) {
      void trigger(erroredFields);
    }
  }, [currentKey, currentType, getFieldState, trigger]);

  // Changing the type changes which format the value must satisfy, in both directions: a value that
  // was valid as text is not necessarily a valid IPv4, and the error raised for the previous type
  // must disappear once a compatible one is picked. Only revalidate a value the user has actually
  // entered, so selecting a type first never greets them with an error on an untouched field.
  useEffect(() => {
    const { isDirty: valueIsDirty, error } = getFieldState('scope_variable_value');
    if (valueIsDirty || error) {
      void trigger('scope_variable_value');
    }
  }, [currentType, getFieldState, trigger]);

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleFormSubmit = (data: VariableFormValues) => {
    onSubmit({
      ...data,
      scope_variable_key: data.scope_variable_key.trim(),
      scope_variable_value: data.scope_variable_value.trim(),
    });
    handleClose();
  };

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={handleClose}
      fullWidth
      maxWidth="sm"
      slotProps={{ paper: { elevation: 1 } }}
    >
      <DialogTitle>{t('Create a new variable')}</DialogTitle>
      <FormProvider {...methods}>
        <form id="scopeVariableForm" onSubmit={handleSubmit(handleFormSubmit)}>
          <DialogContent style={{
            display: 'grid',
            gap: theme.spacing(2),
          }}
          >
            <TextFieldController
              name="scope_variable_key"
              label={t('Key')}
              required
            />
            <SelectFieldController
              name="scope_variable_type"
              label={t('Type')}
              items={typeItems}
              required
            />
            <TextFieldController
              name="scope_variable_value"
              label={t('Value')}
              required
            />
            <TextFieldController
              name="scope_variable_description"
              label={t('Description')}
              multiline
              rows={2}
            />
          </DialogContent>
          <DialogActions>
            <Button variant="outlined" color="primary" onClick={handleClose} disabled={isSubmitting}>
              {t('Cancel')}
            </Button>
            <Button
              variant="contained"
              color="primary"
              type="submit"
              disabled={!isDirty || isSubmitting}
            >
              {t('Create')}
            </Button>
          </DialogActions>
        </form>
      </FormProvider>
    </Dialog>
  );
};

export default ScopeVariableCreateDialog;
