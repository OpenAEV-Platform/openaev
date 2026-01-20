export interface InjectorHelper {
  getInjector: (injectorId: string) => Injector;
  getInjectorsIncludingPending: () => Injector[];
  getInjectorsMap: () => Record<string, Injector>;
}
