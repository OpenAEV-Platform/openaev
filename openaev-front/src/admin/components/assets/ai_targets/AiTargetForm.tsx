import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import MarkingFieldController from '../../../../components/fields/MarkingFieldController';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type AiTargetInput } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import { zodImplement } from '../../../../utils/Zod';
import { CRITICALITY_OPTIONS, humanizeEnum } from '../asset-categories';
import { withoutNullValues } from '../asset-stream-values';

interface Props {
  onSubmit: SubmitHandler<AiTargetInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<AiTargetInput>;
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

const DEFAULT_VALUES: AiTargetInput = {
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
  asset_markings: [],
};

const AiTargetForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing,
  initialValues = {},
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const ability = useAbility();
  const showMarkings = isFeatureEnabled('MARKING') && ability.can(ACTIONS.MANAGE, SUBJECTS.MARKING_ASSIGNMENT);

  const methods = useForm<AiTargetInput>({
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
        asset_markings: z.string().array().optional(),
      }),
    ),
    // Values read from the API carry nulls: they fall back to these defaults.
    defaultValues: {
      ...DEFAULT_VALUES,
      ...withoutNullValues(initialValues),
    },
  });
  const {
    handleSubmit,
    formState: { isDirty, isSubmitting },
  } = methods;

  // Without the picker, the markings are left out: the AI target keeps the ones it has.
  const handleSubmitWithMarkings = handleSubmit(({ asset_markings, ...aiTargetData }) => onSubmit({
    ...aiTargetData,
    ...(showMarkings ? { asset_markings: asset_markings ?? [] } : {}),
  }));

  const providerItems = PROVIDERS.map(provider => ({
    value: provider,
    label: PROVIDER_LABELS[provider],
  }));
  const modalityItems = MODALITIES.map(modality => ({
    value: modality,
    label: t(MODALITY_LABEL_KEYS[modality]),
  }));
  const criticalityItems = CRITICALITY_OPTIONS.map(criticality => ({
    value: criticality,
    label: t(humanizeEnum(criticality)),
  }));

  return (
    <FormProvider {...methods}>
      <form
        noValidate
        id="aiTargetForm"
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        onSubmit={handleSubmitWithMarkings}
      >
        <TextFieldController required name="asset_name" label={t('Name')} />
        <SelectFieldController required name="ai_target_provider" label={t('Provider')} items={providerItems} />
        <SelectFieldController name="ai_target_modality" label={t('Modality')} items={modalityItems} />
        <SelectFieldController name="asset_criticality" label={t('Criticality')} items={criticalityItems} />
        <TextFieldController name="ai_target_endpoint" label={t('Endpoint URL')} placeholder="https://api.openai.com/v1" />
        <TextFieldController name="ai_target_model" label={t('Model')} placeholder="gpt-4o" />
        <TextFieldController name="ai_target_system_prompt" label={t('System prompt (optional)')} multiline rows={3} />
        <TextFieldController
          name="ai_target_token"
          type="password"
          label={t('API token (optional)')}
          helperText={t('Credential used to reach the target. Leave empty for targets that require no authentication.')}
        />
        <TextFieldController name="asset_description" label={t('Description')} multiline rows={2} />
        <TagFieldController name="asset_tags" label={t('Tags')} />
        {showMarkings && (
          <MarkingFieldController name="asset_markings" label={t('Markings')} />
        )}

        <div style={{ alignSelf: 'flex-end' }}>
          <Button type="button" priority="secondary" onClick={handleClose} disabled={isSubmitting} style={{ marginRight: theme.spacing(2) }}>
            {t('Cancel')}
          </Button>
          <Button type="submit" disabled={!isDirty || isSubmitting}>
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default AiTargetForm;
