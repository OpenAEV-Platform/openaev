import { type User } from '../../utils/api-types';

export type UserType = 'PLATFORM' | 'TENANT';

export interface UserResult {
  entities: { users: Record<string, User> };
  result: string;
}
