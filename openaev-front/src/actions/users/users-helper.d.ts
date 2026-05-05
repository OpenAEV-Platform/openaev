import { type User, type UserInput } from '../../utils/api-types';
import type { Option } from '../../utils/Option';

export type UserInputForm = Omit<UserInput, 'user_organization' | 'user_tags' | 'user_tenants'> & {
  user_organization?: Option;
  user_tags?: Option[];
  user_tenants?: Option[];
};

export type UserType = 'PLATFORM' | 'TENANT';

export interface UserResult {
  entities: { users: Record<string, User> };
  result: string;
}
