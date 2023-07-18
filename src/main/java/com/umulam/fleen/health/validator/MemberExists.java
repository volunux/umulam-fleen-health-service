package com.umulam.fleen.health.validator;

import com.umulam.fleen.health.validator.impl.CountryExistsValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CountryExistsValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MemberExists {

  String message() default "Member does not exists";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}