import { type User, type UserInput } from '../../utils/api-types';
import type { Option } from '../../utils/Option';

export type UserInputForm = Omit<UserInput, 'user_organization' | 'user_tags'> & {
  user_organization?: Option;
  user_tags?: Option[];
};

export interface UserResult {
  entities: { users: Record<string, User> };
  result: string;
}
