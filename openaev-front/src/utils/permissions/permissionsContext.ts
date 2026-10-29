import { AbilityProvider, Can as CaslCan, useAbility as useCaslAbility } from '@casl/react';
import { type ComponentType, createElement } from 'react';

import { type AppAbility } from './ability';

export { AbilityProvider };

export const Can = CaslCan<AppAbility>;

export const useAbility = () => useCaslAbility<AppAbility>();

// Class components cannot call hooks: inject the ability as an `ability` prop instead.
export function withAbility<P extends { ability: AppAbility }>(WrappedComponent: ComponentType<P>) {
  const WithAbility = (props: Omit<P, 'ability'>) => createElement(WrappedComponent, {
    ...props,
    ability: useAbility(),
  } as P);
  WithAbility.displayName = `withAbility(${WrappedComponent.displayName ?? WrappedComponent.name})`;
  return WithAbility;
}
