package com.umulam.fleen.health.validator;

import com.umulam.fleen.health.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Slf4j
@Component
public class EmailAddressExistsValidator implements ConstraintValidator<EmailAddressExists, String> {

  private final MemberService service;

  public EmailAddressExistsValidator(MemberService service) {
    this.service = service;
  }

  @Override
  public void initialize(EmailAddressExists emailAddressExists) {}

  @Override
  public boolean isValid(String emailAddress, ConstraintValidatorContext context) {
    return !(service.isMemberExists(emailAddress));
  }
}

