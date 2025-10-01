import { type Article, type Channel } from '../../utils/api-types';

export type FullArticleStore = Article & { article_fullchannel: Channel };
