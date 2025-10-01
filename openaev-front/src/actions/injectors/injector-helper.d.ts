import { type Injector } from '../../utils/api-types';

export interface InjectorHelper {
  getInjector: (injectorId: string) => Injector;
  getInjectors: () => Injector[];
  getInjectorsMap: () => Record<string, Injector>;
}
