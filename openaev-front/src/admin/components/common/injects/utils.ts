import type { FieldValues } from 'react-hook-form';
import { z, type ZodType } from 'zod/v4';

import { type Translate } from '../../../../components/i18n';
import type { Inject } from '../../../../utils/api-types';
import type { ContractElement } from '../../../../utils/api-types-custom';

/**
 * Distinct targeting counts across a list of injects: how many unique assets
 * and asset groups are directly targeted. Drives the usage-aware hero stats
 * of scenarios and simulations (technical dimension).
 */
export const countDistinctInjectTargets = (injects: Inject[]): {
  assets: number;
  assetGroups: number;
  /** Distinct targeted asset group ids: asset groups carry no scenario /
   * simulation scope on their ES documents, so the hero-stat drill-down
   * scopes the results list with this explicit id list instead. */
  assetGroupIds: string[];
} => {
  const assets = new Set<string>();
  const assetGroups = new Set<string>();
  injects.forEach((inject) => {
    (inject.inject_assets ?? []).forEach(id => assets.add(id));
    (inject.inject_asset_groups ?? []).forEach(id => assetGroups.add(id));
  });
  return {
    assets: assets.size,
    assetGroups: assetGroups.size,
    assetGroupIds: [...assetGroups],
  };
};

export const isInjectContentType = (type: ContractElement['type']) => type !== 'asset' && type !== 'team' && type !== 'asset-group' && type !== 'article' && type !== 'challenge' && type !== 'attachment';

export const isRequiredField = (field: ContractElement, fields: ContractElement[], values: FieldValues) => {
  if (field.mandatory) {
    return true;
  } else if (field.mandatoryConditionFields?.length) {
    let mandatory = true;

    field.mandatoryConditionFields.forEach((fieldMandatoryConditionField) => {
      let value;
      const fieldMandatoryConditionFieldType = fields.find(f => f.key === fieldMandatoryConditionField)?.type;
      if (fieldMandatoryConditionFieldType && isInjectContentType(fieldMandatoryConditionFieldType)) {
        value = values.inject_content[fieldMandatoryConditionField];
      } else {
        value = values[fieldMandatoryConditionField];
      }
      if (!field.mandatoryConditionValues?.[fieldMandatoryConditionField] && (value === undefined || value === null || value.length === 0)) {
        mandatory = false;
      } else if (field.visibleConditionValues?.[fieldMandatoryConditionField] && String(value) !== String(field.visibleConditionValues?.[fieldMandatoryConditionField])) {
        mandatory = false;
      }
    });
    return mandatory;
  }
  return false;
};

export const isVisibleField = (field: ContractElement, fields: ContractElement[], values: FieldValues) => {
  if (!field.visibleConditionFields?.length) return true;

  let visible = true;
  field.visibleConditionFields.forEach((fieldVisibleConditionField) => {
    let value;
    const fieldVisibleConditionFieldType = fields.find(f => f.key === fieldVisibleConditionField)?.type;

    if (fieldVisibleConditionFieldType && isInjectContentType(fieldVisibleConditionFieldType)) {
      value = values.inject_content[fieldVisibleConditionField];
    } else {
      value = values[fieldVisibleConditionField];
    }

    const conditionValues = field.visibleConditionValues?.[fieldVisibleConditionField];

    if (!conditionValues && (value === undefined || value === null || value.length === 0)) {
      visible = false;
    } else if (conditionValues) {
      if (Array.isArray(conditionValues)) {
        if (!conditionValues.includes(value)) {
          visible = false;
        }
      } else if (String(value) !== String(conditionValues)) {
        visible = false;
      }
    }
  });
  return visible;
};

export const getValidatingRule = (field: ContractElement, t: Translate) => {
  let rule: ZodType = z.any();
  switch (field.type) {
    case 'number':
      rule = z.number();
      break;

    case 'checkbox':
      rule = z.boolean();
      break;
    case 'tags':
      rule = z.array(z.string()).min(1, { message: t('Required') }).default([]);
      break;

    case 'text':
    case 'textarea':
    case 'select':
    case 'choice':
    case 'dependency-select':
    case 'ai-target':
      rule = z.string().min(1, { message: t('Required') });
      break;

    case 'attachment':
    case 'asset':
    case 'asset-group':
    case 'targeted-asset':
    case 'payload':
    case 'team':
    case 'expectation':
    case 'article':
    case 'challenge':
      rule = z
        .array(z.any())
        .min(1, { message: t('Required') })
        .default([]);
      break;

    default:
      rule = z.any();
  }

  return rule;
};
