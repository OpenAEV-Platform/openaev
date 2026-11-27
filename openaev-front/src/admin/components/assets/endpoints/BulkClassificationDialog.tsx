import { Button, Text } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { Box } from '@mui/material';
import { type FunctionComponent, useEffect } from 'react';
import { FormProvider, type Resolver, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';

import Dialog from '../../../../components/common/dialog/Dialog';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import type { AssetBulkUpdateInput } from '../../../../utils/api-types';
import {
  ASSET_EDITABLE_CATEGORIES,
  type AssetCategory,
  type AssetCriticality,
  type AssetSubCategory,
  criticalityItems,
  getCategoryDef,
  humanizeEnum,
  subcategoryBelongsTo,
} from '../asset-categories';

/** The fields of a bulk update: an empty field leaves every selected asset's value as it is. */
export type BulkClassificationValues = Pick<
  AssetBulkUpdateInput,
  'asset_category' | 'asset_subcategory' | 'asset_url' | 'asset_criticality' | 'asset_tags_to_add'
>;

interface FormValues {
  asset_category: string;
  asset_subcategory: string;
  asset_url: string;
  asset_criticality: string;
  asset_tags_to_add: string[];
}

interface Props {
  numberOfSelectedElements: number;
  onClose: () => void;
  onSubmit: (values: BulkClassificationValues) => Promise<void>;
}

/** Builds the bulk update body: only the fields the person filled are sent. */
// eslint-disable-next-line react-refresh/only-export-components
export const toBulkClassificationInput = (values: FormValues): BulkClassificationValues => ({
  asset_category: (values.asset_category || undefined) as AssetCategory | undefined,
  asset_subcategory: (values.asset_subcategory || undefined) as AssetSubCategory | undefined,
  asset_url: values.asset_url.trim() || undefined,
  asset_criticality: (values.asset_criticality || undefined) as AssetCriticality | undefined,
  asset_tags_to_add: values.asset_tags_to_add.length > 0 ? values.asset_tags_to_add : undefined,
});

const SubcategoryField: FunctionComponent = () => {
  const { t } = useFormatter();
  const category = useWatch<FormValues, 'asset_category'>({ name: 'asset_category' });
  const subcategories = category ? getCategoryDef(category as AssetCategory).subcategories : [];
  const items = subcategories.map(s => ({
    value: s as string,
    label: t(humanizeEnum(s)),
  }));
  if (!category || items.length === 0) {
    return null;
  }
  return (
    <SelectFieldController
      name="asset_subcategory"
      label={t('Subcategory')}
      items={items}
      fullWidth
      helperText={t('Narrower type within the chosen category. Empty: each asset keeps its subcategory.')}
    />
  );
};

/** URL help line: a category that requires a URL says so, since an asset moved there without one asks for it at its next edit. */
const UrlField: FunctionComponent = () => {
  const { t } = useFormatter();
  const category = useWatch<FormValues, 'asset_category'>({ name: 'asset_category' });
  const def = category ? getCategoryDef(category as AssetCategory) : null;
  const helperText = def?.fields.url === 'required'
    ? t('{category} requires a URL: an asset without one will ask for it at its next edit. Empty: each asset keeps its URL.', { category: t(def.label) })
    : t('Address the scans and the web injectors target. Empty: each asset keeps its URL.');
  return <TextFieldController name="asset_url" label={t('URL')} helperText={helperText} />;
};

const BulkClassificationDialog: FunctionComponent<Props> = ({ numberOfSelectedElements, onClose, onSubmit }) => {
  const { t } = useFormatter();

  const schema = z.object({
    asset_category: z.string(),
    asset_subcategory: z.string(),
    asset_url: z.string(),
    asset_criticality: z.string(),
    asset_tags_to_add: z.string().array(),
  }).superRefine((values, ctx) => {
    // Same rules as the API (issue #9653): a subcategory needs its category and belongs to it.
    if (values.asset_subcategory
      && !subcategoryBelongsTo(values.asset_subcategory as AssetSubCategory, (values.asset_category || null) as AssetCategory | null)) {
      ctx.addIssue({
        code: 'custom',
        path: ['asset_subcategory'],
        message: t('This subcategory does not belong to the category'),
      });
    }
    const input = toBulkClassificationInput(values);
    if (Object.values(input).every(v => v === undefined)) {
      ctx.addIssue({
        code: 'custom',
        path: ['asset_category'],
        message: t('Set at least one field to update'),
      });
    }
  });

  const methods = useForm<FormValues>({
    mode: 'onTouched',
    resolver: zodResolver(schema) as Resolver<FormValues>,
    defaultValues: {
      asset_category: '',
      asset_subcategory: '',
      asset_url: '',
      asset_criticality: '',
      asset_tags_to_add: [],
    },
  });
  const { handleSubmit, formState: { isSubmitting }, setValue, getValues, control } = methods;

  // A category change drops a subcategory of the previous category.
  const category = useWatch({
    control,
    name: 'asset_category',
  });
  useEffect(() => {
    const subcategory = getValues('asset_subcategory');
    if (subcategory && !subcategoryBelongsTo(subcategory as AssetSubCategory, (category || null) as AssetCategory | null)) {
      setValue('asset_subcategory', '');
    }
  }, [category]);

  const categoryItems = ASSET_EDITABLE_CATEGORIES.map(c => ({
    value: c as string,
    label: t(getCategoryDef(c).label),
  }));

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(toBulkClassificationInput(values));
    } catch {
      // Keep the values so the user can retry; the error is already surfaced globally.
    }
  });

  const handleClose = () => {
    if (isSubmitting) return;
    onClose();
  };

  return (
    <Dialog open handleClose={handleClose} title={t('Edit classification')} maxWidth="sm" showCloseIcon>
      <FormProvider {...methods}>
        <form
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            submit(e);
          }}
        >
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            mt: 1,
          }}
          >
            <Text variant="content-base" className="text-default-secondary">
              {t('Selected assets: {count}. A field left empty keeps each asset\'s own value; tags are added to the ones each asset already carries.', { count: String(numberOfSelectedElements) })}
            </Text>
            <SelectFieldController
              name="asset_category"
              label={t('Category')}
              items={categoryItems}
              fullWidth
              helperText={t('Decides which fields each asset shows. Empty: each asset keeps its category.')}
            />
            <SubcategoryField />
            <UrlField />
            <SelectFieldController
              name="asset_criticality"
              label={t('Criticality')}
              items={criticalityItems(t)}
              fullWidth
              helperText={t('Impact of a compromise, from Very high (stops the business) to Low (little effect); Unknown when not assessed. Empty: each asset keeps its value.')}
            />
            <TagFieldController
              name="asset_tags_to_add"
              label={t('Tags to add')}
              helperText={t('Added to the tags each asset already carries; none is removed.')}
            />
            <Box sx={{ textAlign: 'right' }}>
              <Button type="button" priority="secondary" onClick={handleClose} disabled={isSubmitting} style={{ marginRight: 16 }}>
                {t('Cancel')}
              </Button>
              <Button type="submit" loading={isSubmitting}>
                {t('Update')}
              </Button>
            </Box>
          </Box>
        </form>
      </FormProvider>
    </Dialog>
  );
};

export default BulkClassificationDialog;
