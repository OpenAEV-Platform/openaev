import { type User, type UserInput } from '../../utils/api-types';

export type UserInputForm = UserInput;

export type UserType = 'PLATFORM' | 'TENANT';

export interface UserResult {
  entities: { users: Record<string, User> };
  result: string;
}
