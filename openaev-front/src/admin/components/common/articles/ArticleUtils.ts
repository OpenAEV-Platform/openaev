import { z } from 'zod';

import type { ArticleCreateInput, ArticleUpdateInput, Channel, Document, Tag } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

// Extract the validation logic of the Mime Types
export const isMimeTypeValid = (docType: string | undefined, allowedTypes: string[]) => {
  if (allowedTypes.length === 0) return true;
  return allowedTypes.some(mime => (docType ?? '').includes(mime));
};

// Extract the logic from the Tags
export const matchesTags = (docTags: string[] | undefined, selectedTags: Tag[]) => {
  if (selectedTags.length === 0) return true;
  const safeDocTags = docTags ?? [];
  return selectedTags.some(tag => safeDocTags.includes(tag.tag_id));
};

// Extract the text search
export const matchesSearch = (doc: Document, keyword: string) => {
  if (!keyword) return true;
  const lowerKeyword = keyword.toLowerCase();
  return (
    (doc.document_name ?? '').toLowerCase().includes(lowerKeyword)
    || (doc.document_description ?? '').toLowerCase().includes(lowerKeyword)
  );
};

export const resolveChannelId = (articleChannel: string | Channel): string => {
  if (!articleChannel) {
    return '';
  }

  if (typeof articleChannel === 'object') {
    return articleChannel.channel_id;
  }

  return articleChannel;
};

// `ArticleCreateInput` and `ArticleUpdateInput` share the same shape: one schema
// drives both the creation and the edition form.
export type ArticleFormInput = ArticleCreateInput & ArticleUpdateInput;

// Number inputs hand back strings from the DOM: coerce them so the API receives
// real numbers. The cast keeps `zodImplement` happy, whose input type is the model.
const optionalCount = z.coerce.number().optional() as unknown as z.ZodOptional<z.ZodType<number, number>>;

export const articleFormSchema = (t: (text: string) => string) => zodImplement<ArticleFormInput>().with({
  article_name: z.string().min(1, t('This field is required.')),
  article_channel: z.string().min(1, t('This field is required.')),
  article_author: z.string().optional(),
  article_content: z.string().optional(),
  article_documents: z.array(z.string()).optional(),
  article_comments: optionalCount,
  article_shares: optionalCount,
  article_likes: optionalCount,
  article_published: z.boolean().optional(),
});
