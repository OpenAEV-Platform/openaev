import type { Option } from '../../../../utils/Option';
import { type UpdateUserInput } from '../../utils/api-types';

export type UserInputForm = Omit<
  UpdateUserInput,
    'user_organization' | 'user_tags'
> & {
  user_organization: Option | undefined;
  user_tags: Option[];
};

export interface UserResult {
  entities: { users: Record<string, User> };
  result: string;
}
