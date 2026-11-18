package io.openaev.database.model.hash;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Stays out of the content hash: platform state, identity, provenance or relations. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotHashed {}
