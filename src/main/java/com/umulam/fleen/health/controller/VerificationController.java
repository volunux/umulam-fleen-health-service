package com.umulam.fleen.health.controller;

import com.umulam.fleen.health.model.dto.authentication.ChangePasswordDto;
import com.umulam.fleen.health.model.dto.authentication.ConfirmMfaDto;
import com.umulam.fleen.health.model.dto.authentication.ResendVerificationCodeDto;
import com.umulam.fleen.health.model.dto.authentication.VerificationCodeDto;
import com.umulam.fleen.health.model.response.FleenHealthResponse;
import com.umulam.fleen.health.model.response.authentication.SignInResponse;
import com.umulam.fleen.health.model.response.authentication.SignUpResponse;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.service.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static com.umulam.fleen.health.constant.base.GeneralConstant.AUTH_HEADER_KEY;
import static com.umulam.fleen.health.constant.base.GeneralConstant.AUTH_HEADER_PREFIX;
import static com.umulam.fleen.health.constant.base.FleenHealthConstant.PASSWORD_CHANGED_UPDATED;

@Slf4j
@RestController
@RequestMapping(value = "verification")
public class VerificationController {

  private final AuthenticationService authenticationService;

  public VerificationController(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @PostMapping(value = "/confirm-sign-up")
  @PreAuthorize("hasAnyRole('PRE_VERIFIED_USER', 'PRE_VERIFIED_PROFESSIONAL', 'PRE_VERIFIED_BUSINESS')")
  public SignUpResponse completeSignUp(@AuthenticationPrincipal FleenUser user,
                                       @Valid @RequestBody VerificationCodeDto dto) {
    return authenticationService.completeSignUp(dto, user);
  }

  @PostMapping(value = "/resend-pre-verification-code")
  @PreAuthorize("hasAnyRole('PRE_VERIFIED_USER', 'PRE_VERIFIED_PROFESSIONAL', 'PRE_VERIFIED_BUSINESS')")
  public FleenHealthResponse resendPreVerificationCode(
          @AuthenticationPrincipal FleenUser user,
          @Valid @RequestBody ResendVerificationCodeDto dto) {
    return authenticationService.resendPreVerificationCode(dto, user);
  }

  @GetMapping(value = "/refresh-token")
  @PreAuthorize("hasRole('REFRESH_TOKEN_USER')")
  public SignInResponse refreshToken(@AuthenticationPrincipal FleenUser user,
                                     @RequestHeader(value = AUTH_HEADER_KEY) String token) {
    token = StringUtils.removeStartIgnoreCase(token, AUTH_HEADER_PREFIX.concat(" "));
    return authenticationService.refreshToken(user.getUsername(), token);
  }

  @PostMapping(value = "/validate-sign-in-mfa")
  @PreAuthorize("hasRole('PRE_AUTHENTICATED_USER')")
  public SignInResponse validateSignInMfa(
          @AuthenticationPrincipal FleenUser user,
          @Valid @RequestBody ConfirmMfaDto dto) {
    return authenticationService.validateSignInMfa(user, dto);
  }

  @PostMapping(value = "/resend-pre-authentication-code")
  @PreAuthorize("hasAnyRole('PRE_AUTHENTICATED_USER')")
  public FleenHealthResponse resendPreAuthenticationCode(
          @AuthenticationPrincipal FleenUser user,
          @Valid @RequestBody ResendVerificationCodeDto dto) {
    return authenticationService.resendPreAuthenticationCode(dto, user);
  }

  @PostMapping(value = "/reset-change-password")
  @PreAuthorize("hasRole('RESET_PASSWORD_USER')")
  public FleenHealthResponse resetAndChangePassword(
          @AuthenticationPrincipal FleenUser user,
          @Valid @RequestBody ChangePasswordDto dto) {
    authenticationService.changePassword(user.getUsername(), dto);
    return new FleenHealthResponse(PASSWORD_CHANGED_UPDATED);
  }

  @PostMapping(value = "/complete-onboarding")
  @PreAuthorize("hasRole('PRE_ONBOARDED')")
  public SignInResponse completeOnboarding(@AuthenticationPrincipal FleenUser user,
                                            @Valid @RequestBody ChangePasswordDto dto) {
    return authenticationService.completeOnboarding(user.getUsername(), dto);
  }
}
