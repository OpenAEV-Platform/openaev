package io.openbas.stix.parsing;

import io.openbas.stix.objects.DomainObject;

public interface StixDomainObjectConvertible {
  DomainObject toStixDomainObject();
}
