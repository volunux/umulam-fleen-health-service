package com.umulam.fleen.health.constant;

import lombok.Getter;

@Getter
public enum CommonEmailMessageTemplateDetails {

  PRE_VERIFICATION("pre-verification.ftl", "Complete Sign Up", null),
  PRE_AUTHENTICATION("pre-authentication.ftl", "Complete Sign in", null),
  FORGOT_PASSWORD("forgot-password.ftl", "Forgot Password", "Unable to complete forgot password operation, no email body available.");

  private final String templateName;
  private final String emailMessageSubject;
  private final String noEmailFoundMessage;

  CommonEmailMessageTemplateDetails(String templateName, String emailMessageSubject, String noEmailFoundMessage) {
    this.templateName = templateName;
    this.emailMessageSubject = emailMessageSubject;
    this.noEmailFoundMessage = noEmailFoundMessage;
  }
}