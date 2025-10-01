package io.openaev.stix.parsing;

import io.openaev.stix.objects.DomainObject;

public interface StixDomainObjectConvertible {
  DomainObject toStixDomainObject();
}
