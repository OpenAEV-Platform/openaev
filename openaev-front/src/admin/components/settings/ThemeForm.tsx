import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { type FunctionComponent, useEffect } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import ColorPickerField from '../../../components/ColorPickerField';
import TextFieldFds from '../../../components/fields/TextFieldFds';
import { useFormatter } from '../../../components/i18n';
import { type ThemeInput } from '../../../utils/api-types';
import { FORM_HEX_COLOR_REGEX } from '../../../utils/Colors';
import { type FdsThemeMode } from '../../../utils/hooks/useFdsThemeScope';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { CONTRAST_FLOOR, themeContrastWarnings } from '../../../utils/themeContrast';
import { zodImplement } from '../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<ThemeInput>;
  initialValues?: ThemeInput;
  canNotManage: boolean;
  /** The mode this form edits: an unset colour is judged against that mode's token. */
  mode: FdsThemeMode;
}

const useStyles = makeStyles()(() => ({ field: { marginBottom: 20 } }));

/** Every field this form owns, empty: what "default" means for a theme. */
const EMPTY_THEME: ThemeInput = {
  accent_color: '',
  background_color: '',
  login_aside_color: '',
  login_aside_gradient_end: '',
  login_aside_gradient_start: '',
  login_aside_image: '',
  logo_login_url: '',
  logo_url: '',
  logo_url_collapsed: '',
  navigation_color: '',
  paper_color: '',
  primary_color: '',
  secondary_color: '',
  text_color: '',
};

const ThemeForm: FunctionComponent<Props> = ({
  onSubmit,
  mode,
  initialValues = EMPTY_THEME,
  canNotManage,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  // Empty means "default"; anything else must be #RRGGBB.
  const optionalColor = z
    .string()
    .refine(value => value === '' || FORM_HEX_COLOR_REGEX.test(value), { message: t('Color must be a valid hex value, e.g. #4CAF50') })
    .optional();

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
    watch,
    reset,
  } = useForm<ThemeInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ThemeInput>().with({
        accent_color: optionalColor,
        background_color: optionalColor,
        login_aside_color: optionalColor,
        login_aside_gradient_end: optionalColor,
        login_aside_gradient_start: optionalColor,
        login_aside_image: z.string().optional(),
        logo_login_url: z.string().optional(),
        logo_url: z.string().optional(),
        logo_url_collapsed: z.string().optional(),
        navigation_color: optionalColor,
        paper_color: optionalColor,
        primary_color: optionalColor,
        secondary_color: optionalColor,
        text_color: optionalColor,
      }),
    ),
    defaultValues: initialValues,
  });

  useEffect(() => {
    reset(initialValues);
  }, [initialValues, reset]);

  // Warning only: a stored theme is never rewritten, and nothing is blocked.
  const [background, paper, primary, textColor] = watch([
    'background_color', 'paper_color', 'primary_color', 'text_color',
  ]);
  const hasAnyValue = Object.values(watch()).some(value => !!value);
  const warnings = themeContrastWarnings(
    {
      background,
      paper,
      primary,
      text: textColor,
    },
    mode,
  );
  const belowFloor = (ratio: number) => t('Contrast below the accessibility floor')
    + ` (${ratio.toFixed(2)}:1 < ${CONTRAST_FLOOR}:1)`;

  return (
    <form id="themeForm" onSubmit={handleSubmit(onSubmit)}>

      <ColorPickerField
        className={classes.field}
        label={t('Background color')}
        placeholder={t('Default')}
        control={control}
        name="background_color"
        disabled={canNotManage}
        helperText={warnings.background !== undefined ? belowFloor(warnings.background) : undefined}
      />
      <ColorPickerField
        className={classes.field}
        label={t('Paper color')}
        placeholder={t('Default')}
        control={control}
        name="paper_color"
        disabled={canNotManage}
      />
      <ColorPickerField
        className={classes.field}
        label={t('Navigation color')}
        placeholder={t('Default')}
        control={control}
        name="navigation_color"
        disabled={canNotManage}
      />
      <ColorPickerField
        className={classes.field}
        label={t('Primary color')}
        placeholder={t('Default')}
        control={control}
        name="primary_color"
        disabled={canNotManage}
        helperText={warnings.primary !== undefined ? belowFloor(warnings.primary) : undefined}
      />
      <ColorPickerField
        className={classes.field}
        label={t('Secondary color')}
        placeholder={t('Default')}
        control={control}
        name="secondary_color"
        disabled={canNotManage}
      />
      <ColorPickerField
        className={classes.field}
        label={t('Accent color')}
        placeholder={t('Default')}
        control={control}
        name="accent_color"
        disabled={canNotManage}
      />
      <ColorPickerField
        className={classes.field}
        label={t('Text color')}
        placeholder={t('Default')}
        control={control}
        name="text_color"
        disabled={canNotManage}
        helperText={warnings.text !== undefined ? belowFloor(warnings.text) : undefined}
      />
      <TextFieldFds
        className={classes.field}
        label={t('Logo URL')}
        placeholder={t('Default')}

        error={!!errors.logo_url}
        helperText={errors.logo_url && errors.logo_url?.message}
        {...register('logo_url')}
        disabled={canNotManage}
      />
      <TextFieldFds
        className={classes.field}
        label={t('Logo URL (collapsed)')}
        placeholder={t('Default')}

        error={!!errors.logo_url_collapsed}
        helperText={errors.logo_url_collapsed && errors.logo_url_collapsed?.message}
        {...register('logo_url_collapsed')}
        disabled={canNotManage}
      />
      <TextFieldFds
        className={classes.field}
        label={t('Logo URL (login)')}
        placeholder={t('Default')}

        error={!!errors.logo_login_url}
        helperText={errors.logo_login_url && errors.logo_login_url?.message}
        {...register('logo_login_url')}
        disabled={canNotManage}
      />
      {/* Login page aside customization (aligned with OpenCTI):
          priority is image > gradient > color > default Filigran gradient. */}
      <ColorPickerField
        className={classes.field}
        label={t('Login aside color')}
        placeholder={t('Default')}
        control={control}
        name="login_aside_color"
        disabled={canNotManage}
      />
      <ColorPickerField
        className={classes.field}
        label={t('Login aside gradient start color')}
        placeholder={t('Default')}
        control={control}
        name="login_aside_gradient_start"
        disabled={canNotManage}
      />
      <ColorPickerField
        className={classes.field}
        label={t('Login aside gradient end color')}
        placeholder={t('Default')}
        control={control}
        name="login_aside_gradient_end"
        disabled={canNotManage}
      />
      <TextFieldFds
        label={t('Login aside image URL')}
        placeholder={t('Default')}

        error={!!errors.login_aside_image}
        helperText={errors.login_aside_image && errors.login_aside_image?.message}
        {...register('login_aside_image')}
        disabled={canNotManage}
      />

      <div style={{
        marginTop: 20,
        display: 'flex',
        justifyContent: 'flex-end',
        gap: 8,
      }}
      >
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
          {/* Clears the block back to the library's own values. Nothing is stored
              until Update, so the choice stays reversible. */}
          <Button
            type="button"
            priority="secondary"
            disabled={isSubmitting || !hasAnyValue}
            onClick={() => reset(EMPTY_THEME, { keepDefaultValues: true })}
          >
            {t('Reset')}
          </Button>
          <Button type="submit" disabled={!isDirty || isSubmitting}>
            {t('Update')}
          </Button>
        </Can>
      </div>
    </form>
  );
};

export default ThemeForm;
