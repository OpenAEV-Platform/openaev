import { schema } from 'normalizr';

export const article = new schema.Entity(
  'articles',
  {},
  { idAttribute: 'article_id' },
);
export const arrayOfArticles = new schema.Array(article);
