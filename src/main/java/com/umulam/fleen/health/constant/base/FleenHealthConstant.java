package com.umulam.fleen.health.constant.base;

public class FleenHealthConstant {

  public static final String PAYSTACK_INITIALIZE_PAYMENT_URL = "https://api.paystack.co/transaction/initialize";
  public static final String PAYSTACK_VERIFY_PAYMENT_URL = "https://api.paystack.co/transaction/verify/";
  private static final String GOOGLE_RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
  public static final String PRE_VERIFICATION_EMAIL_SUBJECT = "Complete Sign Up";
  public static final String FORGOT_PASSWORD_EMAIL_SUBJECT = "Forgot Password";
  public static final String VERIFICATION_CODE_KEY = "code";
  public static final String PRE_VERIFICATION_TEMPLATE_NAME = "pre-verification.ftl";
  public static final String LOGO_FILE_NAME = "logo.png";
  public static final String VERIFICATION_CODE_MESSAGE = "Verification code sent";
  public static final String FORGOT_PASSWORD_TEMPLATE_NAME = "forgot-password.ftl";
  public static final String FAIL_MAIL_DELIVERY = "Mail Delivery failed";
  public static final String PASSWORD_CHANGED_UPDATED = "Password changed and updated successfully";

}
