import { type PlayerInput, type User } from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';

export type PlayerInputForm = Omit<
  PlayerInput,
'user_organization' | 'user_country' | 'user_tags'
> & {
  user_organization: Option | undefined;
  user_country: Option | undefined;
  user_tags: Option[];
};
export type UserStore = Omit<User, 'user_organization' | 'user_tags', | 'user_teams'> & {
  user_organization: string | undefined;
  user_tags: string[] | undefined;
  user_teams: string[] | undefined;
};
