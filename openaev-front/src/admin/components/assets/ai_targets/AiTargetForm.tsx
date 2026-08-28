import { zodResolver } from '@hookform/resolvers/zod';
import { Button, MenuItem, TextField } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Controller, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TagField from '../../../../components/fields/TagField';
import { useFormatter } from '../../../../components/i18n';
import { type AiTargetInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';
import { CRITICALITY_OPTIONS, humanizeEnum } from '../asset-categories';

interface Props {
  onSubmit: SubmitHandler<AiTargetInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: AiTargetInput;
}

const PROVIDERS = [
  'OPENAI_COMPATIBLE',
  'ANTHROPIC',
  'AZURE_OPENAI',
  'AWS_BEDROCK',
  'GOOGLE_VERTEX',
  'HUGGINGFACE',
  'OLLAMA',
  'CUSTOM_HTTP',
  'MCP_SERVER',
  'AGENT_HTTP',
  'XTM_ONE',
] as const;

// Provider names are proper nouns - displayed as-is, never passed through t().
const PROVIDER_LABELS: Record<(typeof PROVIDERS)[number], string> = {
  OPENAI_COMPATIBLE: 'OpenAI-compatible',
  ANTHROPIC: 'Anthropic',
  AZURE_OPENAI: 'Azure OpenAI',
  AWS_BEDROCK: 'AWS Bedrock',
  GOOGLE_VERTEX: 'Google Vertex',
  HUGGINGFACE: 'Hugging Face',
  OLLAMA: 'Ollama',
  CUSTOM_HTTP: 'Custom HTTP',
  MCP_SERVER: 'MCP server',
  AGENT_HTTP: 'Agent HTTP',
  XTM_ONE: 'XTM One',
};

const MODALITIES = ['TEXT', 'VISION', 'AUDIO', 'MULTIMODAL'] as const;

// i18n keys (Text / Vision / Audio / Multimodal) for the modality enum values.
const MODALITY_LABEL_KEYS: Record<(typeof MODALITIES)[number], string> = {
  TEXT: 'Text',
  VISION: 'Vision',
  AUDIO: 'Audio',
  MULTIMODAL: 'Multimodal',
};

const AiTargetForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing,
  initialValues = {
    asset_name: '',
    ai_target_provider: 'OPENAI_COMPATIBLE',
    ai_target_modality: 'TEXT',
    ai_target_endpoint: '',
    ai_target_model: '',
    ai_target_system_prompt: '',
    ai_target_token: '',
    asset_criticality: 'UNKNOWN',
    asset_description: '',
    asset_tags: [],
    asset_external_reference: undefined,
  },
}) => {
  const { t } = useFormatter();

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<AiTargetInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<AiTargetInput>().with({
        asset_name: z.string().min(1, { message: t('Should not be empty') }),
        ai_target_provider: z.enum(PROVIDERS),
        ai_target_modality: z.enum(MODALITIES).optional(),
        ai_target_endpoint: z.string().optional().nullable(),
        ai_target_model: z.string().optional().nullable(),
        ai_target_system_prompt: z.string().optional().nullable(),
        ai_target_token: z.string().optional().nullable(),
        ai_target_configuration: z.record(z.string(), z.unknown()).optional(),
        asset_criticality: z.enum(['VERY_HIGH', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN']).optional(),
        asset_description: z.string().optional(),
        asset_tags: z.string().array().optional(),
        asset_external_reference: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  return (
    <form id="aiTargetForm" onSubmit={handleSubmit(onSubmit)}>
      <TextField
        variant="standard"
        fullWidth
        label={t('Name')}
        style={{ marginTop: 10 }}
        error={!!errors.asset_name}
        helperText={errors.asset_name?.message}
        {...register('asset_name')}
        required
      />
      <Controller
        control={control}
        name="ai_target_provider"
        rules={{ required: true }}
        render={({ field }) => (
          <TextField
            select
            variant="standard"
            fullWidth
            value={field.value}
            label={t('Provider')}
            style={{ marginTop: 20 }}
            error={!!errors.ai_target_provider}
            helperText={errors.ai_target_provider?.message}
            {...register('ai_target_provider')}
            required
          >
            {PROVIDERS.map(provider => (
              <MenuItem key={provider} value={provider}>{PROVIDER_LABELS[provider]}</MenuItem>
            ))}
          </TextField>
        )}
      />
      <Controller
        control={control}
        name="ai_target_modality"
        render={({ field }) => (
          <TextField
            select
            variant="standard"
            fullWidth
            value={field.value ?? 'TEXT'}
            label={t('Modality')}
            style={{ marginTop: 20 }}
            error={!!errors.ai_target_modality}
            helperText={errors.ai_target_modality?.message}
            {...register('ai_target_modality')}
          >
            {MODALITIES.map(modality => (
              <MenuItem key={modality} value={modality}>{t(MODALITY_LABEL_KEYS[modality])}</MenuItem>
            ))}
          </TextField>
        )}
      />
      <Controller
        control={control}
        name="asset_criticality"
        render={({ field }) => (
          <TextField
            select
            variant="standard"
            fullWidth
            value={field.value ?? 'UNKNOWN'}
            label={t('Criticality')}
            style={{ marginTop: 20 }}
            error={!!errors.asset_criticality}
            helperText={errors.asset_criticality?.message}
            {...register('asset_criticality')}
          >
            {CRITICALITY_OPTIONS.map(criticality => (
              <MenuItem key={criticality} value={criticality}>{t(humanizeEnum(criticality))}</MenuItem>
            ))}
          </TextField>
        )}
      />
      <TextField
        variant="standard"
        fullWidth
        label={t('Endpoint URL')}
        placeholder="https://api.openai.com/v1"
        style={{ marginTop: 20 }}
        error={!!errors.ai_target_endpoint}
        helperText={errors.ai_target_endpoint?.message}
        {...register('ai_target_endpoint')}
      />
      <TextField
        variant="standard"
        fullWidth
        label={t('Model')}
        placeholder="gpt-4o"
        style={{ marginTop: 20 }}
        error={!!errors.ai_target_model}
        helperText={errors.ai_target_model?.message}
        {...register('ai_target_model')}
      />
      <TextField
        variant="standard"
        fullWidth
        multiline
        rows={3}
        label={t('System prompt (optional)')}
        style={{ marginTop: 20 }}
        error={!!errors.ai_target_system_prompt}
        helperText={errors.ai_target_system_prompt?.message}
        {...register('ai_target_system_prompt')}
      />
      <TextField
        variant="standard"
        fullWidth
        type="password"
        label={t('API token (optional)')}
        style={{ marginTop: 20 }}
        error={!!errors.ai_target_token}
        helperText={
          errors.ai_target_token?.message
          ?? t('Credential used to reach the target. Leave empty for targets that require no authentication.')
        }
        {...register('ai_target_token')}
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
        {...register('asset_description')}
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
          variant="outlined"
          color="primary"
          onClick={handleClose}
          style={{ marginRight: 10 }}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="primary"
          type="submit"
          disabled={!isDirty || isSubmitting}
        >
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default AiTargetForm;
