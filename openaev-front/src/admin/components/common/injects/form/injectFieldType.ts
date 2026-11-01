import { type EnhancedContractElement } from '../../../../../utils/api-types-custom';

export type InjectFieldType = EnhancedContractElement['type'] | 'richText' | 'document';

/**
 * The widget an inject content field renders as. A payload argument of type `document` reaches the
 * contract as a plain text field that only keeps the type in `argumentType`: it is rendered as the
 * document picker the payload form uses for its default value, storing the document id the
 * executor resolves (#6973).
 */
export const resolveInjectFieldType = (field: Pick<EnhancedContractElement, 'type' | 'richText' | 'argumentType'>): InjectFieldType => {
  if (field.argumentType === 'document' && field.type === 'text') {
    return 'document';
  }
  if (field.type === 'textarea' && field.richText) {
    return 'richText';
  }
  return field.type;
};
