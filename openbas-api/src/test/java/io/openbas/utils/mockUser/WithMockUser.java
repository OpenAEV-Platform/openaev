package io.openbas.utils.mockUser;

import io.openbas.database.model.Capability;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WithMockUser {
  String userFirstName() default "";

  String userLastName() default "";

  String userMail() default "";

  boolean isAdmin() default false;

  Capability[] withCapabilities() default {};
}
