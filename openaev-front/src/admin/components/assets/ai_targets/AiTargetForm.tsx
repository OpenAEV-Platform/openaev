import { Button, Select, SelectContent, SelectHelperText, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { type FunctionComponent } from 'react';
import { Controller, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import MarkingField from '../../../../components/fields/MarkingField';
import TagField from '../../../../components/fields/TagField';
import TextFieldFds from '../../../../components/fields/TextFieldFds';
import { useFormatter } from '../../../../components/i18n';
import { type AiTargetInput } from '../../../../utils/api-types';
import markingIdSetsEqual from '../../../../utils/markings';
import { isFeatureEnabled } from '../../../../utils/utils';
import { zodImplement } from '../../../../utils/Zod';
import { CRITICALITY_OPTIONS, humanizeEnum } from '../asset-categories';

// Markings are not part of AiTargetInput: like endpoints, they live on a dedicated, more
// heavily-guarded endpoint (PUT /api/assets/{id}/markings) rather than the generic asset update,
// so they cannot ride along in the same payload. This form still hosts the field (an AI target
// already being edited has an id to assign markings against) but reports changes to it
// separately, through `onMarkingsChange`, instead of folding them into `onSubmit`'s AiTargetInput.
type AiTargetFormValues = AiTargetInput & { asset_markings?: string[] | null };

interface Props {
  /**
   * May resolve to `false` to report failure - `SubmitHandler`'s own return type (`unknown`)
   * already allows this. When markings were also changed this submit, `false` here triggers a
   * compensating revert - see `onMarkingsChange` and `handleSubmitWithMarkings`.
   */
  onSubmit: SubmitHandler<AiTargetInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: AiTargetInput & { asset_markings?: string[] | null };
  /**
   * Called on submit with the full replacement marking id list, and awaited before `onSubmit`
   * runs - see AssetForm's `onMarkingsChange` doc for the full race/failure-handling rationale
   * this mirrors. Only invoked while editing, and only when the picker was available and its
   * selection actually changed from what the form opened with. Resolves to `false` on failure
   * (the caller has already shown its own error notification for it) - a `false` here cancels
   * `onSubmit` too, so the form stays open on a half-applied save.
   *
   * Also called a second time, with the pre-submit id list, to revert the markings PUT if it
   * committed but `onSubmit` then failed - see `handleSubmitWithMarkings`.
   */
  onMarkingsChange?: (markingIds: string[]) => boolean | Promise<boolean>;
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
  onMarkingsChange,
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

  // Assigning markings requires an asset id, so the picker only makes sense once the AI target
  // exists - gated on `editing` the same way the field itself is only reported through
  // `onMarkingsChange`, and on the flag the rest of the marking UI is gated on.
  const showMarkings = editing && isFeatureEnabled('MARKING');

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<AiTargetFormValues>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<AiTargetFormValues>().with({
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
        // .nullable() matters here: the backend's markingIds field (Asset.java) has no default
        // initializer, so an AI target with no markings assigned comes back as literal `null`
        // rather than `[]` - .optional() alone rejects that and silently blocks every submit.
        asset_markings: z.string().array().optional().nullable(),
      }),
    ),
    defaultValues: initialValues,
  });

  // Markings are split out of the submitted payload and reported through `onMarkingsChange`
  // instead (see the `AiTargetFormValues` comment above), and only when the picker was available
  // and its selection actually moved from what the form opened with - see AssetForm's identically
  // named handler for the full race/failure-handling rationale this mirrors.
  const handleSubmitWithMarkings = handleSubmit(async ({ asset_markings, ...aiTargetData }) => {
    const initialIds = initialValues.asset_markings ?? [];
    let markingsChanged = false;
    if (showMarkings) {
      const currentIds = asset_markings ?? [];
      markingsChanged = !markingIdSetsEqual(currentIds, initialIds);
      if (markingsChanged) {
        const markingsSaved = await onMarkingsChange?.(currentIds);
        // `false` means the markings PUT failed - its own error notification already fired - so
        // don't also run the asset update: better to leave the form open on a save the user can
        // see failed than to report success for the fields that did go through while the
        // markings silently didn't.
        if (markingsSaved === false) {
          return;
        }
      }
    }

    const saved = await onSubmit(aiTargetData);

    // The markings PUT already committed but the rest of the save didn't - compensate by
    // reverting markings to what the form opened with, rather than leaving the AI target with new
    // markings and none of the other changes. Best-effort and silent, same as AssetForm: the
    // asset/markings split stays an implementation detail, not something surfaced to the user - a
    // failed revert still gets the same generic error notification any other failed PUT would.
    if (saved === false && markingsChanged) {
      await onMarkingsChange?.(initialIds);
    }
  });

  return (
    <form noValidate id="aiTargetForm" onSubmit={handleSubmitWithMarkings}>
      <TextFieldFds
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
          <div style={{ marginTop: 20 }}>
            <Select
              value={field.value ?? ''}
              onValueChange={field.onChange}
              name={field.name}
              error={!!errors.ai_target_provider}
              required
            >
              <SelectLabel required>{t('Provider')}</SelectLabel>
              <SelectTrigger className="w-full">
                <SelectValue placeholder={t('Provider')} />
              </SelectTrigger>
              <SelectContent>
                {PROVIDERS.map(provider => (
                  <SelectItem key={provider} value={provider}>{PROVIDER_LABELS[provider]}</SelectItem>
                ))}
              </SelectContent>
              {errors.ai_target_provider?.message ? <SelectHelperText>{errors.ai_target_provider?.message}</SelectHelperText> : null}
            </Select>
          </div>
        )}
      />
      <Controller
        control={control}
        name="ai_target_modality"
        render={({ field }) => (
          <div style={{ marginTop: 20 }}>
            <Select
              value={field.value ?? 'TEXT'}
              onValueChange={field.onChange}
              name={field.name}
              error={!!errors.ai_target_modality}
            >
              <SelectLabel>{t('Modality')}</SelectLabel>
              <SelectTrigger className="w-full">
                <SelectValue placeholder={t('Modality')} />
              </SelectTrigger>
              <SelectContent>
                {MODALITIES.map(modality => (
                  <SelectItem key={modality} value={modality}>{t(MODALITY_LABEL_KEYS[modality])}</SelectItem>
                ))}
              </SelectContent>
              {errors.ai_target_modality?.message ? <SelectHelperText>{errors.ai_target_modality?.message}</SelectHelperText> : null}
            </Select>
          </div>
        )}
      />
      <Controller
        control={control}
        name="asset_criticality"
        render={({ field }) => (
          <div style={{ marginTop: 20 }}>
            <Select
              value={field.value ?? 'UNKNOWN'}
              onValueChange={field.onChange}
              name={field.name}
              error={!!errors.asset_criticality}
            >
              <SelectLabel>{t('Criticality')}</SelectLabel>
              <SelectTrigger className="w-full">
                <SelectValue placeholder={t('Criticality')} />
              </SelectTrigger>
              <SelectContent>
                {CRITICALITY_OPTIONS.map(criticality => (
                  <SelectItem key={criticality} value={criticality}>{t(humanizeEnum(criticality))}</SelectItem>
                ))}
              </SelectContent>
              {errors.asset_criticality?.message ? <SelectHelperText>{errors.asset_criticality?.message}</SelectHelperText> : null}
            </Select>
          </div>
        )}
      />
      <TextFieldFds
        label={t('Endpoint URL')}
        placeholder="https://api.openai.com/v1"
        style={{ marginTop: 20 }}
        error={!!errors.ai_target_endpoint}
        helperText={errors.ai_target_endpoint?.message}
        {...register('ai_target_endpoint')}
      />
      <TextFieldFds
        label={t('Model')}
        placeholder="gpt-4o"
        style={{ marginTop: 20 }}
        error={!!errors.ai_target_model}
        helperText={errors.ai_target_model?.message}
        {...register('ai_target_model')}
      />
      <TextFieldFds
        multiline
        rows={3}
        label={t('System prompt (optional)')}
        style={{ marginTop: 20 }}
        error={!!errors.ai_target_system_prompt}
        helperText={errors.ai_target_system_prompt?.message}
        {...register('ai_target_system_prompt')}
      />
      <TextFieldFds
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
      <TextFieldFds
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
      {showMarkings && (
        <Controller
          control={control}
          name="asset_markings"
          render={({ field: { onChange, value }, fieldState: { error } }) => (
            <MarkingField
              label={t('Markings')}
              fieldValue={value ?? []}
              fieldOnChange={onChange}
              error={error}
              style={{ marginTop: 20 }}
            />
          )}
        />
      )}
      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button type="button" priority="secondary" onClick={handleClose} disabled={isSubmitting} style={{ marginRight: 10 }}>
          {t('Cancel')}
        </Button>
        <Button type="submit" disabled={!isDirty || isSubmitting}>
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default AiTargetForm;
