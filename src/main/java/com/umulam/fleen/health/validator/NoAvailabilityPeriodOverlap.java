package com.umulam.fleen.health.validator;

import com.umulam.fleen.health.validator.impl.IsNumberValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = IsNumberValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoAvailabilityPeriodOverlap {

  String message() default "No overlapping time slots allowed";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
