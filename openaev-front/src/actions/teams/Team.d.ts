import { type TeamUpdateInput } from '../../utils/api-types';
import { type Option } from '../../utils/Option';

export type TeamInputForm = Omit<
  TeamUpdateInput,
'team_organization' | 'team_tags'
> & {
  team_organization: Option | undefined;
  team_tags: Option[];
};
