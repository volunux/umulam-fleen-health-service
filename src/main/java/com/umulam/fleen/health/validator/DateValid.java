package com.umulam.fleen.health.validator;

import com.umulam.fleen.health.validator.impl.DateValidValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = DateValidValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DateValid {

  String message() default "Date is invalid";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
