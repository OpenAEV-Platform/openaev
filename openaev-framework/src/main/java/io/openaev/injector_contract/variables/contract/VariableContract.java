package io.openaev.injector_contract.variables.contract;

import io.openaev.database.model.Variable;
import io.openaev.injector_contract.ContractCardinality;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface VariableContract {
  String name();

  String description() default "";

  Variable.VariableType type() default Variable.VariableType.String;

  ContractCardinality cardinality() default ContractCardinality.One;
}
