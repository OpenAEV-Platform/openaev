import { type UpdateUserInput } from '../../../../utils/api-types';
import type { Option } from '../../../../utils/Option';

export type UserInputForm = Omit<
  UpdateUserInput,
    'user_organization' | 'user_tags'
> & {
  user_organization: Option | undefined;
  user_tags: Option[];
};
