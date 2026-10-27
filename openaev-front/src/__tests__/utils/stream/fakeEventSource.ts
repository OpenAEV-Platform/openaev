type Listener = (event: MessageEvent) => void;

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  url: string;
  closed = false;
  onerror: (() => void) | null = null;
  private listeners = new Map<string, Set<Listener>>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: Listener) {
    const set = this.listeners.get(type) ?? new Set();
    set.add(listener);
    this.listeners.set(type, set);
  }

  close() {
    this.closed = true;
  }

  emit(type: string, data?: unknown) {
    const event = { data: data === undefined ? undefined : JSON.stringify(data) } as MessageEvent;
    this.listeners.get(type)?.forEach(listener => listener(event));
  }

  emitRaw(type: string, data: string) {
    this.listeners.get(type)?.forEach(listener => listener({ data } as MessageEvent));
  }

  listenerCount(type: string) {
    return this.listeners.get(type)?.size ?? 0;
  }

  static reset() {
    FakeEventSource.instances = [];
  }

  static get last() {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1];
  }
}

export default FakeEventSource;
