import type { Tag } from '../../utils/api-types';

export interface TagHelper {
  getTag: (tagId: Tag['tag_id']) => Tag;
  getTags: () => Tag[];
  getTagsMap: () => Record<string, Tag>;
}
