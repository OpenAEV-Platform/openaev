package io.openaev.aop.lock;

public enum LockResourceType {
  INJECT(4096),
  PAYLOAD(4096),
  SECURITY_COVERAGE(4096),
  MANAGER_FACTORY(1);

  private final int stripes;

  LockResourceType(int stripes) {
    this.stripes = stripes;
  }

  public int stripes() {
    return stripes;
  }
}
