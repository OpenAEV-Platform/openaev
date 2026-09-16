export interface ProtectedResourceOutput { protected_resource?: boolean }

export const isProtectedResource = (resource?: ProtectedResourceOutput | null): boolean =>
  resource?.protected_resource === true;
