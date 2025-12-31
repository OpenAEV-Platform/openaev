export interface InjectorHelper {
  getInjector: (injectorId: string) => Injector;
  getInjectors: () => Injector[];
  getInjectorsMap: () => Record<string, Injector>;
}
