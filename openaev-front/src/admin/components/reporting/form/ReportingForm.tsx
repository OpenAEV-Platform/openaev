import { Button, Checkbox, IconButton, Paper as FdsPaper, Radio, RadioGroup, Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { DragDropContext, Draggable, Droppable, type DropResult } from '@hello-pangea/dnd';
import { zodResolver } from '@hookform/resolvers/zod';
import { CodeOutlined, DeleteOutlined, DragIndicatorOutlined, PictureAsPdfOutlined, RestartAltOutlined } from '@mui/icons-material';
// fds:keep-mui the text-labelled ToggleButtonGroup stays on MUI: the library ButtonGroup items are icon-only (LIBRARY-FEEDBACK #57)
import { Box, FormHelperText, Paper, Step, StepLabel, Stepper, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { Controller, FormProvider, type SubmitHandler, useFieldArray, useForm, useFormContext } from 'react-hook-form';
import { z } from 'zod';

import { type LoggedHelper } from '../../../../actions/helper';
import { fetchKillChainPhases } from '../../../../actions/KillChainPhase';
import ColorPickerField from '../../../../components/ColorPickerField';
import DocumentField from '../../../../components/fields/DocumentField';
import MarkDownFieldController from '../../../../components/fields/MarkDownFieldController';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import SwitchFieldController from '../../../../components/fields/SwitchFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import TextFieldFds from '../../../../components/fields/TextFieldFds';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import {
  type PlatformSettings,
  type Reporting,
  type ReportingInput,
  type ReportingScheduleInput,
  type TenantSettingsOutput,
  type ThemeInput,
} from '../../../../utils/api-types';
import { layerInputVars } from '../../../../utils/fdsLayer';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { type Option } from '../../../../utils/Option';
import useKillChains from '../../common/filters/useKillChains';
import { MODULE_TYPE_LABELS, TIME_RANGE_LABELS } from '../render/reportingRenderLabels';
import { REPORTING_CONTEXT_ICONS, REPORTING_CONTEXT_LABELS } from '../ReportingContexts';
import {
  type BrandingColorSeed,
  DEFAULT_MODULE_TYPES,
  MODULE_TYPE_DESCRIPTIONS,
  platformBrandingSeed,
  REPORTING_CONTEXT_TYPES,
  REPORTING_FORMATS,
  REPORTING_MODULE_TYPES,
  REPORTING_TIME_RANGES,
  type ReportingContextType,
  type ReportingFormat,
  type ReportingModuleType,
  type ReportingThemeMode,
  type ReportingTimeRange,
  resolveSubjectOptions,
  searchSubjectOptions,
} from '../ReportingFormUtils';
import ReportingAutocompleteField from './ReportingAutocompleteField';
import ReportingScheduleFields, { type ReportingScheduleFieldsValues } from './ReportingScheduleFields';
import { buildScheduleFieldsSchema, scheduleInputFromValues, scheduleValuesFromSchedule, validateScheduleTime } from './reportingScheduleUtils';

const LOGO_EXTENSIONS = ['png', 'jpg', 'jpeg', 'svg', 'gif', 'webp'];

interface ReportingModuleFormEntry {
  module_type: ReportingModuleType;
  module_title: string;
  content: string;
  /** MITRE_COVERAGE only: kill chain names to cover; empty = all kill chains. */
  kill_chains: string[];
}

export interface ReportingFormValues extends ReportingScheduleFieldsValues {
  reporting_name: string;
  reporting_description: string;
  reporting_context_type: ReportingContextType;
  reporting_context_id: string;
  reporting_time_range: ReportingTimeRange;
  reporting_default_format: ReportingFormat;
  modules: ReportingModuleFormEntry[];
  branding_theme_mode: ReportingThemeMode;
  branding_primary_color: string;
  branding_secondary_color: string;
  branding_accent_color: string;
  branding_background_color: string;
  branding_paper_color: string;
  branding_text_color: string;
  branding_logo_document_id: string;
}

const buildDefaultValues = (
  reporting: Reporting | undefined,
  platformMode: ReportingThemeMode,
  seedFor: (mode: ReportingThemeMode) => BrandingColorSeed,
): ReportingFormValues => {
  const mode = reporting?.reporting_branding?.theme_mode ?? platformMode;
  const seed = seedFor(mode);
  const branding = reporting?.reporting_branding;
  return {
    reporting_name: reporting?.reporting_name ?? '',
    reporting_description: reporting?.reporting_description ?? '',
    reporting_context_type: reporting?.reporting_context_type ?? 'PLATFORM',
    reporting_context_id: reporting?.reporting_context_id ?? '',
    reporting_time_range: reporting?.reporting_time_range ?? 'LAST_30_DAYS',
    reporting_default_format: reporting?.reporting_default_format ?? 'PDF',
    modules: reporting
      ? (reporting.reporting_modules ?? []).map(module => ({
          // Persisted modules always carry a type; the spec only marks it
          // optional because the entity field has no @NotNull.
          module_type: module.module_type ?? 'COVER',
          module_title: module.module_title ?? '',
          content: typeof module.module_config?.content === 'string' ? module.module_config.content : '',
          kill_chains: Array.isArray(module.module_config?.kill_chains)
            ? (module.module_config.kill_chains as unknown[]).filter((name): name is string => typeof name === 'string')
            : [],
        }))
      : DEFAULT_MODULE_TYPES.map(type => ({
          module_type: type,
          module_title: '',
          content: '',
          kill_chains: [],
        })),
    branding_theme_mode: mode,
    branding_primary_color: branding?.primary_color ?? seed.primary_color,
    branding_secondary_color: branding?.secondary_color ?? seed.secondary_color,
    branding_accent_color: branding?.accent_color ?? seed.accent_color,
    branding_background_color: branding?.background_color ?? seed.background_color,
    branding_paper_color: branding?.paper_color ?? seed.paper_color,
    branding_text_color: branding?.text_color ?? seed.text_color,
    branding_logo_document_id: branding?.logo_document_id ?? '',
    ...scheduleValuesFromSchedule(),
    schedule_enabled: false,
  };
};

interface Props {
  onSubmit: (input: ReportingInput, schedule?: ReportingScheduleInput) => Promise<void>;
  handleClose: () => void;
  initialValues?: Reporting;
  editing?: boolean;
}

/**
 * Report creation / edition wizard (drawer content): subject, modules,
 * branding and - on creation only - an optional first schedule (schedules are
 * then managed from the report detail page).
 */
/** The optional title, its label on the left: the row stays one line high. */
const ModuleTitleField = ({ id, name }: {
  id: string;
  name: `modules.${number}.module_title`;
}) => {
  const { t } = useFormatter();
  const { control } = useFormContext<ReportingFormValues>();
  return (
    <Box sx={{
      flex: 1,
      display: 'flex',
      alignItems: 'center',
      gap: 1,
    }}
    >
      <Typography
        component="label"
        htmlFor={id}
        variant="body2"
        sx={{
          color: 'text.secondary',
          whiteSpace: 'nowrap',
        }}
      >
        {t('Custom title (optional)')}
      </Typography>
      <Controller
        control={control}
        name={name}
        render={({ field }) => <TextFieldFds {...field} id={id} value={field.value ?? ''} />}
      />
    </Box>
  );
};

// The default-format group is named by the caption above it.
const FORMAT_LABEL_ID = 'reporting-default-format-label';

// One glyph per output: the page icon for the printed document, the markup
// icon for the page served in a browser.
const REPORTING_FORMAT_ICONS = {
  PDF: PictureAsPdfOutlined,
  HTML: CodeOutlined,
} as const;

const ReportingForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues,
  editing = false,
}) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  // Simulation options are labelled "name (date)": many simulations share the
  // same name, so the friendly start date disambiguates them in the picker.
  const formatSimulationDate = (startDate?: string | null) => (startDate ? nsdt(startDate) : '');

  const { settings, tenantSettings }: {
    settings: PlatformSettings;
    tenantSettings: TenantSettingsOutput;
  } = useHelper((helper: LoggedHelper) => ({
    settings: helper.getPlatformSettings(),
    tenantSettings: helper.getTenantSettings(),
  }));

  const themeConfigFor = (mode: ReportingThemeMode): ThemeInput | undefined => (mode === 'LIGHT'
    ? tenantSettings?.platform_light_theme ?? settings?.platform_light_theme
    : tenantSettings?.platform_dark_theme ?? settings?.platform_dark_theme);
  const seedFor = (mode: ReportingThemeMode) => platformBrandingSeed(mode, themeConfigFor(mode));
  const platformMode: ReportingThemeMode = (tenantSettings?.platform_theme || settings?.platform_theme) === 'light' ? 'LIGHT' : 'DARK';

  const validationSchema = useMemo(
    () => z.object({
      reporting_name: z.string().min(1, { message: t('Should not be empty') }),
      reporting_description: z.string(),
      reporting_context_type: z.enum(REPORTING_CONTEXT_TYPES),
      reporting_context_id: z.string(),
      reporting_time_range: z.enum(REPORTING_TIME_RANGES),
      reporting_default_format: z.enum(REPORTING_FORMATS),
      modules: z.array(z.object({
        module_type: z.enum(REPORTING_MODULE_TYPES),
        module_title: z.string(),
        content: z.string(),
        kill_chains: z.array(z.string()),
      })).min(1, { message: t('Select at least one module') }),
      branding_theme_mode: z.enum(['LIGHT', 'DARK']),
      branding_primary_color: z.string(),
      branding_secondary_color: z.string(),
      branding_accent_color: z.string(),
      branding_background_color: z.string(),
      branding_paper_color: z.string(),
      branding_text_color: z.string(),
      branding_logo_document_id: z.string(),
      ...buildScheduleFieldsSchema(),
    }).superRefine((values, ctx) => {
      if (values.reporting_context_type !== 'PLATFORM' && !values.reporting_context_id) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['reporting_context_id'],
          message: t('Should not be empty'),
        });
      }
      if (values.schedule_enabled) {
        validateScheduleTime(values, ctx, t);
      }
    }),
    [],
  );

  const methods = useForm<ReportingFormValues>({
    mode: 'onTouched',
    resolver: zodResolver(validationSchema),
    defaultValues: buildDefaultValues(initialValues, platformMode, seedFor),
  });

  const {
    control,
    handleSubmit,
    watch,
    getValues,
    setValue,
    trigger,
    formState: { isSubmitting, errors },
  } = methods;

  const { fields: moduleFields, append: appendModule, remove: removeModule, move: moveModule } = useFieldArray({
    control,
    name: 'modules',
  });

  // -- Stepper ---------------------------------------------------------------

  // Schedules are managed from the detail page once the report exists.
  // 'Report subject', not the legacy 'Subject' key: that one translates to
  // "Email subject" (email inject forms).
  const stepLabels = editing
    ? ['Report subject', 'Modules', 'Branding']
    : ['Report subject', 'Modules', 'Branding', 'Schedule'];
  const [activeStep, setActiveStep] = useState(0);
  const isLastStep = activeStep === stepLabels.length - 1;

  // Fields validated before leaving each step.
  const stepFields: (keyof ReportingFormValues)[][] = [
    ['reporting_name', 'reporting_context_id'],
    ['modules'],
    [],
    ['schedule_time'],
  ];

  const handleNext = async () => {
    const valid = await trigger(stepFields[activeStep]);
    if (valid) setActiveStep(step => step + 1);
  };

  // -- Subject entity options -------------------------------------------------

  const contextType = watch('reporting_context_type');
  const [subjectOptions, setSubjectOptions] = useState<Option[]>([]);

  useEffect(() => {
    if (contextType === 'PLATFORM') {
      setSubjectOptions([]);
      return undefined;
    }
    let cancelled = false;
    const currentId = getValues('reporting_context_id');
    Promise.all([
      searchSubjectOptions(contextType, '', formatSimulationDate),
      currentId ? resolveSubjectOptions(contextType, [currentId], formatSimulationDate) : Promise.resolve([] as Option[]),
    ]).then(([searched, resolved]) => {
      if (cancelled) return;
      const searchedIds = new Set(searched.map(option => option.id));
      setSubjectOptions([...resolved.filter(option => !searchedIds.has(option.id)), ...searched]);
    }).catch(() => {
      if (!cancelled) setSubjectOptions([]);
    });
    return () => {
      cancelled = true;
    };
  }, [contextType]);

  // -- Modules helpers ---------------------------------------------------------

  // Kill chain names for the coverage module scoping (ATT&CK, ATLAS, custom...):
  // derived from the kill chain phase referential, loaded here on demand.
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchKillChainPhases());
  });
  const { killChains } = useKillChains();
  const killChainOptions: Option[] = killChains.map(name => ({
    id: name,
    label: name,
  }));

  const watchedModules = watch('modules');
  const selectedTypes = watchedModules.map(module => module.module_type);

  const toggleModule = (type: ReportingModuleType) => {
    const index = watchedModules.findIndex(module => module.module_type === type);
    if (index >= 0) {
      removeModule(index);
    } else {
      appendModule({
        module_type: type,
        module_title: '',
        content: '',
        kill_chains: [],
      });
    }
  };

  const onDragEnd = (result: DropResult) => {
    if (!result.destination) return;
    moveModule(result.source.index, result.destination.index);
  };

  const modulesError = errors.modules?.message ?? errors.modules?.root?.message;

  // -- Branding helpers --------------------------------------------------------

  const brandingMode = watch('branding_theme_mode');
  const resetBrandingColors = (mode: ReportingThemeMode = brandingMode) => {
    const seed = seedFor(mode);
    setValue('branding_primary_color', seed.primary_color, { shouldDirty: true });
    setValue('branding_secondary_color', seed.secondary_color, { shouldDirty: true });
    setValue('branding_accent_color', seed.accent_color, { shouldDirty: true });
    setValue('branding_background_color', seed.background_color, { shouldDirty: true });
    setValue('branding_paper_color', seed.paper_color, { shouldDirty: true });
    setValue('branding_text_color', seed.text_color, { shouldDirty: true });
  };

  // -- Submit -------------------------------------------------------------------

  const submit: SubmitHandler<ReportingFormValues> = async (values) => {
    const input: ReportingInput = {
      reporting_name: values.reporting_name,
      reporting_description: values.reporting_description || undefined,
      reporting_context_type: values.reporting_context_type,
      reporting_context_id: values.reporting_context_type === 'PLATFORM' ? undefined : values.reporting_context_id,
      reporting_default_format: values.reporting_default_format,
      reporting_time_range: values.reporting_time_range,
      reporting_modules: values.modules.map((module) => {
        let moduleConfig: Record<string, unknown> | undefined;
        if (module.module_type === 'CUSTOM_MARKDOWN') {
          moduleConfig = { content: module.content };
        } else if (module.module_type === 'MITRE_COVERAGE' && module.kill_chains.length > 0) {
          // Empty selection is stored as no config at all: "all kill chains"
          // stays the implicit default and keeps older reports unchanged.
          moduleConfig = { kill_chains: module.kill_chains };
        }
        return {
          module_type: module.module_type,
          module_title: module.module_title || undefined,
          module_config: moduleConfig,
        };
      }),
      reporting_branding: {
        theme_mode: values.branding_theme_mode,
        primary_color: values.branding_primary_color || undefined,
        secondary_color: values.branding_secondary_color || undefined,
        accent_color: values.branding_accent_color || undefined,
        background_color: values.branding_background_color || undefined,
        paper_color: values.branding_paper_color || undefined,
        text_color: values.branding_text_color || undefined,
        logo_document_id: values.branding_logo_document_id || undefined,
      },
    };
    const schedule = !editing && values.schedule_enabled
      ? scheduleInputFromValues({
          ...values,
          schedule_enabled: true,
        })
      : undefined;
    await onSubmit(input, schedule);
  };

  // -- Steps rendering -----------------------------------------------------------

  const renderSubjectStep = () => (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <TextFieldController name="reporting_name" label={t('Name')} required />
      <TextFieldController name="reporting_description" label={t('Description')} multiline rows={2} />
      <Controller
        control={control}
        name="reporting_context_type"
        render={({ field }) => (
          <div>
            {/* The library Select renders no wrapper of its own; without this div
                the column gap would separate its label from its trigger. */}
            <Select
              value={field.value}
              onValueChange={(next) => {
                field.onChange(next);
                // A subject entity belongs to exactly one type.
                setValue('reporting_context_id', '');
              }}
              name={field.name}
            >
              <SelectLabel>{t('Subject type')}</SelectLabel>
              {/* The trigger shows the label alone; the rows carry the icon. This
                is what `renderValue` did, expressed as the trigger's content. */}
              <SelectTrigger className="w-full">
                <span>{t(REPORTING_CONTEXT_LABELS[field.value as ReportingContextType])}</span>
              </SelectTrigger>
              <SelectContent>
                {REPORTING_CONTEXT_TYPES.map((type) => {
                  const TypeIcon = REPORTING_CONTEXT_ICONS[type];
                  return (
                    <SelectItem key={type} value={type}>
                      {/* Radix wraps an item's children in a single span, so the row's own
                        flex never reaches them: without this the glyph sits on the text
                        baseline with no gap. */}
                      <span style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                      >
                        <TypeIcon fontSize="small" color="primary" />
                        {t(REPORTING_CONTEXT_LABELS[type])}
                      </span>
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
          </div>
        )}
      />
      {contextType !== 'PLATFORM' && (
        <Controller
          control={control}
          name="reporting_context_id"
          render={({ field, fieldState: { error } }) => (
            <ReportingAutocompleteField
              label={t(REPORTING_CONTEXT_LABELS[contextType])}
              required
              error={!!error}
              helperText={error?.message}
              value={field.value || undefined}
              onChange={value => field.onChange(value ?? '')}
              options={subjectOptions}
              onInputChange={(search: string) => {
                searchSubjectOptions(contextType, search, formatSimulationDate)
                  .then(setSubjectOptions)
                  .catch(() => setSubjectOptions([]));
              }}
            />
          )}
        />
      )}
      <SelectFieldController
        name="reporting_time_range"
        label={t('Time range')}
        required
        items={REPORTING_TIME_RANGES.map(value => ({
          value,
          label: t(TIME_RANGE_LABELS[value]),
        }))}
      />
      <Controller
        control={control}
        name="reporting_default_format"
        render={({ field }) => (
          <div>
            {/* The group has no visible label of its own, so the caption above
                names it through aria-labelledby. */}
            <Typography id={FORMAT_LABEL_ID} variant="caption" sx={{ color: 'text.secondary' }}>
              {t('Default format')}
            </Typography>
            <RadioGroup
              orientation="horizontal"
              aria-labelledby={FORMAT_LABEL_ID}
              value={field.value}
              onValueChange={value => field.onChange(value)}
              style={{ marginTop: theme.spacing(1) }}
            >
              {REPORTING_FORMATS.map((format) => {
                const FormatIcon = REPORTING_FORMAT_ICONS[format];
                return (
                  <Radio
                    key={format}
                    value={format}
                    label={(
                      // `flex`, not `inline-flex`: an inline box inside the
                      // library's label span sits on its baseline, which left
                      // the icon and the text 2.75px above the circle. A block
                      // box makes the span its own height, and the row's
                      // `items-center` then lines the three up.
                      <span style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: theme.spacing(0.5),
                      }}
                      >
                        <FormatIcon fontSize="small" />
                        {format}
                      </span>
                    )}
                  />
                );
              })}
            </RadioGroup>
          </div>
        )}
      />
    </Box>
  );

  const renderModulesStep = () => (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Typography variant="h3" sx={{ margin: 0 }}>{t('Available modules')}</Typography>
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
        gap: 1,
      }}
      >
        {REPORTING_MODULE_TYPES.map((type) => {
          const selected = selectedTypes.includes(type);
          return (
            // The card is the checkbox's own <label>, so the box is the real
            // control and the whole card stays clickable — through the native
            // label association rather than a handler on a div, which is what
            // made this unreachable by keyboard.
            <Paper
              key={type}
              component="label"
              variant="outlined"
              sx={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 1,
                padding: 1,
                borderRadius: 1,
                cursor: 'pointer',
                borderColor: selected ? 'primary.main' : undefined,
              }}
            >
              <Checkbox
                checked={selected}
                onCheckedChange={() => toggleModule(type)}
              />
              <div>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {t(MODULE_TYPE_LABELS[type])}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {t(MODULE_TYPE_DESCRIPTIONS[type])}
                </Typography>
              </div>
            </Paper>
          );
        })}
      </Box>
      {modulesError && <FormHelperText error>{modulesError}</FormHelperText>}

      <Typography variant="h3" sx={{ margin: 0 }}>{t('Report structure')}</Typography>
      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
        {t('Drag to reorder the sections; titles are optional overrides.')}
      </Typography>
      <DragDropContext onDragEnd={onDragEnd}>
        <Droppable droppableId="reporting-modules">
          {droppableProvided => (
            <div ref={droppableProvided.innerRef} {...droppableProvided.droppableProps}>
              {moduleFields.map((field, index) => {
                const type = watchedModules[index]?.module_type;
                if (!type) return null;
                return (
                  <Draggable key={field.id} draggableId={field.id} index={index}>
                    {draggableProvided => (
                      <FdsPaper
                        ref={draggableProvided.innerRef}
                        {...draggableProvided.draggableProps}
                        padding={8}
                        // One layer above the drawer's surface, like the sibling product's confidence
                        // block; the input aliases follow the layer.
                        elevation={3}
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 8,
                          marginBottom: 8,
                          ...layerInputVars,
                        }}
                      >
                        <Box sx={{
                          display: 'flex',
                          alignItems: 'flex-start',
                          gap: 1,
                        }}
                        >
                          <Box
                            {...draggableProvided.dragHandleProps}
                            sx={{
                              display: 'flex',
                              alignItems: 'center',
                              color: 'text.secondary',
                              cursor: 'grab',
                            }}
                          >
                            <DragIndicatorOutlined fontSize="small" />
                          </Box>
                          <Typography
                            variant="body2"
                            sx={{
                              fontWeight: 600,
                              // Fixed column sized for the longest label ("Performance by
                              // security domain") so every custom title field starts at the
                              // same x; longer translations wrap instead of shifting it.
                              width: 240,
                              flexShrink: 0,
                            }}
                          >
                            {`${index + 1}. ${t(MODULE_TYPE_LABELS[type])}`}
                          </Typography>
                          {/* The kill chain section carries two fields: they stack to the
                              right of the block title, which stays level with the first label. */}
                          {type === 'MITRE_COVERAGE' && (
                            <Box sx={{
                              flex: 1,
                              display: 'flex',
                              flexDirection: 'column',
                              gap: 1,
                            }}
                            >
                              <ModuleTitleField id={`${field.id}-title`} name={`modules.${index}.module_title`} />
                              <Controller
                                control={control}
                                name={`modules.${index}.kill_chains`}
                                render={({ field: killChains }) => (
                                  <ReportingAutocompleteField
                                    multiple
                                    label={t('Kill chains')}
                                    labelPosition="left"
                                    options={killChainOptions}
                                    value={killChains.value}
                                    onChange={killChains.onChange}
                                    onInputChange={() => {}}
                                    helperText={t('Leave empty to cover all kill chains.')}
                                  />
                                )}
                              />
                            </Box>
                          )}
                          {type !== 'MITRE_COVERAGE' && (
                            <>
                              <ModuleTitleField id={`${field.id}-title`} name={`modules.${index}.module_title`} />
                            </>
                          )}
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <IconButton
                                icon={<DeleteOutlined fontSize="small" />}
                                aria-label={t('Remove')}
                                onClick={() => removeModule(index)}
                                variant="destructive"
                                priority="tertiary"
                                size="sm"
                              />
                            </TooltipTrigger>
                            <TooltipContent>{t('Remove')}</TooltipContent>
                          </Tooltip>
                        </Box>
                        {type === 'CUSTOM_MARKDOWN' && (
                          <MarkDownFieldController
                            name={`modules.${index}.content`}
                            label={t('Content')}
                            style={{ marginTop: theme.spacing(1) }}
                            inInject={false}
                          />
                        )}
                      </FdsPaper>
                    )}
                  </Draggable>
                );
              })}
              {droppableProvided.placeholder}
            </div>
          )}
        </Droppable>
      </DragDropContext>
    </Box>
  );

  const renderBrandingStep = () => (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Controller
        control={control}
        name="branding_theme_mode"
        render={({ field }) => (
          <div>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {t('Theme')}
            </Typography>
            <Tooltip>
              <TooltipTrigger asChild>
                <ToggleButtonGroup
                  exclusive
                  size="small"
                  color="primary"
                  value={field.value}
                  onChange={(_, value: ReportingThemeMode | null) => {
                    if (!value || value === field.value) return;
                    field.onChange(value);
                    // The palettes are theme-specific: carrying dark colors into
                    // light mode (or vice versa) would produce unreadable reports.
                    resetBrandingColors(value);
                  }}
                  sx={{ display: 'flex' }}
                >
                  <ToggleButton value="LIGHT" sx={{ flex: 1 }}>{t('Light')}</ToggleButton>
                  <ToggleButton value="DARK" sx={{ flex: 1 }}>{t('Dark')}</ToggleButton>
                </ToggleButtonGroup>
              </TooltipTrigger>
              <TooltipContent>{t('Switching the theme resets the colors to the platform defaults of that theme.')}</TooltipContent>
            </Tooltip>
          </div>
        )}
      />
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
      >
        <Typography variant="h3" sx={{ margin: 0 }}>{t('Colors')}</Typography>
        <Button type="button" priority="tertiary" size="sm" startIcon={<RestartAltOutlined fontSize="small" />} onClick={() => resetBrandingColors()}>
          {t('Reset to platform defaults')}
        </Button>
      </Box>
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: 2,
      }}
      >
        <ColorPickerField label={t('Primary color')} control={control} name="branding_primary_color" />
        <ColorPickerField label={t('Secondary color')} control={control} name="branding_secondary_color" />
        <ColorPickerField label={t('Accent color')} control={control} name="branding_accent_color" />
        <ColorPickerField label={t('Background color')} control={control} name="branding_background_color" />
        <ColorPickerField label={t('Paper color')} control={control} name="branding_paper_color" />
        <ColorPickerField label={t('Text color')} control={control} name="branding_text_color" />
      </Box>
      <Controller
        control={control}
        name="branding_logo_document_id"
        render={({ field, fieldState: { error } }) => (
          <DocumentField
            label={t('Logo (defaults to the platform logo)')}
            fieldValue={field.value}
            fieldOnChange={field.onChange}
            error={error}
            style={{}}
            extensions={LOGO_EXTENSIONS}
          />
        )}
      />
    </Box>
  );

  const renderScheduleStep = () => (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <SwitchFieldController name="schedule_enabled" label={t('Generate this report on a schedule')} />
      {watch('schedule_enabled') && <ReportingScheduleFields />}
    </Box>
  );

  return (
    <FormProvider {...methods}>
      <form
        noValidate
        id="reportingForm"
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(3),
        }}
        onSubmit={handleSubmit(submit)}
      >
        <Stepper activeStep={activeStep}>
          {stepLabels.map((label, index) => (
            <Step key={label}>
              <StepLabel
                onClick={index < activeStep ? () => setActiveStep(index) : undefined}
                sx={{ cursor: index < activeStep ? 'pointer' : 'default' }}
              >
                {t(label)}
              </StepLabel>
            </Step>
          ))}
        </Stepper>

        {activeStep === 0 && renderSubjectStep()}
        {activeStep === 1 && renderModulesStep()}
        {activeStep === 2 && renderBrandingStep()}
        {activeStep === 3 && renderScheduleStep()}

        {/* Buttons follow the form content like every other drawer form in
            the app (no bottom-pinned footer). */}
        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 1,
          marginBottom: 2,
        }}
        >
          <Button type="button" priority="secondary" onClick={handleClose} disabled={isSubmitting}>
            {t('Cancel')}
          </Button>
          {activeStep > 0 && (
            <Button type="button" priority="secondary" onClick={() => setActiveStep(step => step - 1)} disabled={isSubmitting}>
              {t('Back')}
            </Button>
          )}
          {!isLastStep && (
            <Button type="button" onClick={handleNext}>
              {t('Next')}
            </Button>
          )}
          {isLastStep && (
            <Button type="submit" disabled={isSubmitting}>
              {editing ? t('Update') : t('Create')}
            </Button>
          )}
        </Box>
      </form>
    </FormProvider>
  );
};

export default ReportingForm;
