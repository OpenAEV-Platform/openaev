import type { FieldValues } from 'react-hook-form';
import { z, type ZodType } from 'zod/v4';

import { type Translate } from '../../../../components/i18n';
import type { ContractElement } from '../../../../utils/api-types-custom';

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
  if (field.visibleConditionFields?.length) {
    let visible = true;
    field.visibleConditionFields.forEach((fieldVisibleConditionField) => {
      let value;
      const fieldVisibleConditionFieldType = fields.find(f => f.key === fieldVisibleConditionField)?.type;

      if (fieldVisibleConditionFieldType && isInjectContentType(fieldVisibleConditionFieldType)) {
        value = values.inject_content[fieldVisibleConditionField];
      } else {
        value = values[fieldVisibleConditionField];
      }
      if (!field.visibleConditionValues?.[fieldVisibleConditionField] && (value === undefined || value === null || value.length === 0)) {
        visible = false;
      } else if (field.visibleConditionValues?.[fieldVisibleConditionField] && String(value) !== String(field.visibleConditionValues?.[fieldVisibleConditionField])) {
        visible = false;
      }
    });
    return visible;
  }
  return true;
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
      rule = z.array(z.string()).min(1, { message: t('Required') }).default([]); ;
      break;

    case 'text':
    case 'textarea':
    case 'select':
    case 'choice':
    case 'dependency-select':
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
