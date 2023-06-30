package com.umulam.fleen.health.controller;

import com.umulam.fleen.health.model.dto.authentication.ConfirmMfaDto;
import com.umulam.fleen.health.model.dto.authentication.MfaTypeDto;
import com.umulam.fleen.health.model.response.FleenHealthResponse;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.model.security.MfaDetail;
import com.umulam.fleen.health.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping(value = "mfa")
public class MfaController {

  private final MemberService memberService;
  public MfaController(MemberService memberService) {
    this.memberService = memberService;
  }

  @PutMapping(value = "/enable")
  public MfaDetail enableTwoFa(@AuthenticationPrincipal FleenUser user, @Valid @RequestBody MfaTypeDto dto) {
    return memberService.setupMfa(user.getId(), dto);
  }

  @PutMapping(value = "/confirm-mfa")
  public FleenHealthResponse confirmMfa(@AuthenticationPrincipal FleenUser user, @Valid @RequestBody ConfirmMfaDto dto) {
    boolean mfaEnabled = memberService.confirmMfa(user.getUsername(), dto);
    return new FleenHealthResponse("Success");
  }

  @PutMapping(value = "/re-enable")
  public FleenHealthResponse reEnableTwoFa(@AuthenticationPrincipal FleenUser user) {
    memberService.reEnableMfa(user.getId());
    return new FleenHealthResponse("Success");
  }

  @PutMapping(value = "/disable")
  public FleenHealthResponse disableTwoFa(@AuthenticationPrincipal FleenUser user) {
    memberService.disableMfa(user.getId());
    return new FleenHealthResponse("Success");
  }
}
