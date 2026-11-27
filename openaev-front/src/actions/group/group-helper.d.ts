import { type TenantGroupOutput } from '../../utils/api-types';

export interface GroupHelper { getGroup: (groupId: string) => TenantGroupOutput }
