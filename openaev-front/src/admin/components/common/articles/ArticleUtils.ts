import type { Channel, Document, Tag } from '../../../../utils/api-types';

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
