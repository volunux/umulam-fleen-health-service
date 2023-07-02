package com.umulam.fleen.health.service.impl;

import com.umulam.fleen.health.constant.EmailMessageSource;
import com.umulam.fleen.health.constant.MemberStatusType;
import com.umulam.fleen.health.constant.MessageType;
import com.umulam.fleen.health.constant.authentication.MfaType;
import com.umulam.fleen.health.constant.authentication.NextAuthentication;
import com.umulam.fleen.health.constant.authentication.RoleType;
import com.umulam.fleen.health.constant.authentication.VerificationType;
import com.umulam.fleen.health.constant.base.ProfileType;
import com.umulam.fleen.health.constant.verification.ProfileVerificationMessageType;
import com.umulam.fleen.health.constant.verification.ProfileVerificationStatus;
import com.umulam.fleen.health.exception.authentication.*;
import com.umulam.fleen.health.model.SaveProfileVerificationMessageRequest;
import com.umulam.fleen.health.model.domain.*;
import com.umulam.fleen.health.model.dto.authentication.*;
import com.umulam.fleen.health.model.dto.mail.EmailDetails;
import com.umulam.fleen.health.model.json.SmsMessage;
import com.umulam.fleen.health.model.response.FleenHealthResponse;
import com.umulam.fleen.health.model.response.authentication.SignInResponse;
import com.umulam.fleen.health.model.response.authentication.SignUpResponse;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.service.*;
import com.umulam.fleen.health.service.external.aws.EmailServiceImpl;
import com.umulam.fleen.health.service.external.aws.MobileTextService;
import com.umulam.fleen.health.util.JwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;

import static com.umulam.fleen.health.constant.authentication.AuthenticationConstant.*;
import static com.umulam.fleen.health.constant.authentication.AuthenticationStatus.COMPLETED;
import static com.umulam.fleen.health.constant.authentication.AuthenticationStatus.IN_PROGRESS;
import static com.umulam.fleen.health.constant.authentication.MfaType.*;
import static com.umulam.fleen.health.constant.authentication.RoleType.*;
import static com.umulam.fleen.health.constant.authentication.TokenType.ACCESS_TOKEN;
import static com.umulam.fleen.health.constant.authentication.TokenType.REFRESH_TOKEN;
import static com.umulam.fleen.health.constant.base.FleenHealthConstant.*;
import static com.umulam.fleen.health.util.DateTimeUtil.toHours;
import static com.umulam.fleen.health.util.FleenAuthorities.*;

@Slf4j
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

  private final AuthenticationManager authenticationManager;
  private final MemberService memberService;
  private final MemberStatusService memberStatusService;
  private final RoleService roleService;
  private final MfaService mfaService;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final CacheService cacheService;
  private final MobileTextService mobileTextService;
  private final EmailServiceImpl emailService;
  private final VerificationHistoryService verificationHistoryService;
  private final ProfileVerificationMessageService profileVerificationMessageService;

  public AuthenticationServiceImpl(AuthenticationManager authenticationManager,
                                   MemberService memberService,
                                   MemberStatusService memberStatusService,
                                   RoleService roleService,
                                   MfaService mfaService,
                                   PasswordEncoder passwordEncoder,
                                   JwtProvider jwtProvider,
                                   CacheService cacheService,
                                   MobileTextService mobileTextService,
                                   EmailServiceImpl emailService,
                                   VerificationHistoryService verificationHistoryService,
                                   ProfileVerificationMessageService profileVerificationMessageService) {
    this.authenticationManager = authenticationManager;
    this.memberService = memberService;
    this.memberStatusService = memberStatusService;
    this.roleService = roleService;
    this.mfaService = mfaService;
    this.passwordEncoder = passwordEncoder;
    this.jwtProvider = jwtProvider;
    this.cacheService = cacheService;
    this.mobileTextService = mobileTextService;
    this.emailService = emailService;
    this.verificationHistoryService = verificationHistoryService;
    this.profileVerificationMessageService = profileVerificationMessageService;
  }

  /**
   * <p>How the user sign in to get a access and refresh token to use to send requests to and access features of the application or API. The process
   * of validating the credentials i.e. the email address and password is handled by the {@link #authenticate(String, String) authenticate} method.</p>
   * <br/>
   *
   * <p>An entity's (user) authentication status can be {@link com.umulam.fleen.health.constant.authentication.AuthenticationStatus#IN_PROGRESS In Progress}
   * for several reasons for example when the user's profile is yet to complete the registration process initiated at {@link #signUp(SignUpDto) signUp} or be
   * activated. Another reason could be because the user profile has a Multi-Factor Authentication (MFA) enabled. This status works in tandem with
   * {@link NextAuthentication} so that the consumer of the API would be able to know what other authentication needs to be completed and decide what view or
   * UI to display.</p>
   * <br/>
   *
   * <p>A user is not allowed to access the application features or functionalities if their account or profile is disabled.</p>
   * <br/>
   *
   * <p>If the user has completed the registration and has no MFA enabled on their profile, the authentication status is assumed to be completed and
   * and will be allowed to access and use the features of the application as allowed and specified by the role associated with their profile.</p>
   * <br/>
   *
   * <p>When a profile is yet to be activated or the user has not finished the registration process, A user is required to complete a verification process.
   * By default, a One Time Password (OTP) generated through {@link MfaService#generateVerificationOtp(int) generateVerificationOtp} and saved through
   * {@link #savePreVerificationOtp(String, String) savePreVerificationOtp} is sent to the user's registered email address which will be used to complete
   * the registration or sign up at {@link #completeSignUp(VerificationCodeDto, FleenUser) completeSignUp}.</p>
   * <br/>
   *
   * <p>During the login or sign-in process, a access and refresh token with predefined authorities or roles is registered in the authentication context
   * of the user and will be replaced with the original authorities in the DB or store associated with the user's profile after successfully completing
   * the authentication and its stages at {@link com.umulam.fleen.health.controller.VerificationController#validateMfa(FleenUser, ConfirmMfaDto) validateMfa}.
   * This predefined authorities assigned to the authentication context during the sign-in process is to make sure that the user is not allowed to perform
   * actions or access security protected features of the application if the authentication process is yet to be completed.</p>
   * <br/>
   *
   * <p>After verifying the credentials of the user, a session like context is initialized for the user using {@link #cacheService CacheService} through
   * {@link #saveToken(String, String) saveToken}. The existence of this session and user authentication context will be validated on each request that is sent
   * to the API if the request URL is one that only authenticated user can access or the
   * {@link org.springframework.http.HttpHeaders#AUTHORIZATION Authorization} header is present in the request, the request will usually pass through the
   * {@link com.umulam.fleen.health.filter.JwtAuthenticationFilter#doFilter(ServletRequest, ServletResponse, FilterChain) JwtAuthenticationFilter}.</p>
   * <br/>
   *
   * <p>In cases where the MFA enabled on the user's profile is type {@link VerificationType#SMS SMS} or {@link VerificationType#EMAIL EMAIL}. A OTP is sent to
   * the email address or phone number found on their profile, the process of delivering the SMS or EMAIL message is handled and performed by
   * {@link #initPreVerificationOrAuthentication(PreVerificationDto, VerificationType) initPreVerificationOrAuthentication}.</p>
   * <br/>
   *
   * <p>When MFA is enabled on the profile, setting the refresh token to 'NULL' is important and necessary else if the refresh token is set, the user will be able to
   * get a new access token that contains the complete details of the user including the authorities that can be used
   * to access the application features or perform actions on the API.</p>
   * <br/>
   *
   * <p>Other details are set on the {@link SignInResponse} object to allow the consumer of the API or response decide the next action or process to
   * perform. For example, if the user has {@link MfaType MFA} enabled on their profile; they will have to submit a code one sent to their
   * email address or phone number or get the code or OTP from an Authenticator app such as Google Authenticator which is going
   * to be used to complete the authentication process they initiated or started.</p>
   * <br/>
   *
   * @param dto contains a possible user-registered email address and password
   * @return {@link SignInResponse} if the authentication and validation of the user credential is successful.
   * Also contains access token that can be used to access features of the application or API and also the
   * refresh token to use to get a new access token when the previous one has expired.
   * @throws InvalidAuthenticationException if the authentication and validation of the user credential is unsuccessful
   */
  @Override
  public SignInResponse signIn(SignInDto dto) {
    Authentication authentication = authenticate(dto.getEmailAddress(), dto.getPassword());
    if (!authentication.isAuthenticated()) {
      throw new InvalidAuthenticationException(dto.getEmailAddress());
    }

    FleenUser user = (FleenUser) authentication.getPrincipal();
    Role role = user.authoritiesToRoles().get(0);
    String accessToken;
    String refreshToken;

    if (MemberStatusType.DISABLED.name().equals(user.getStatus())) {
      throw new DisabledAccountException();
    }

    SignInResponse signInResponse = SignInResponse
            .builder()
            .emailAddress(user.getEmailAddress())
            .authenticationStatus(IN_PROGRESS)
            .nextAuthentication(NextAuthentication.NONE)
            .mfaEnabled(false)
            .build();

    RoleType roleType = RoleType.valueOf(role.getCode());
    if (MemberStatusType.INACTIVE.name().equals(user.getStatus())
        && (PRE_VERIFIED_USER == roleType ||
            PRE_VERIFIED_PROFESSIONAL == roleType ||
            PRE_VERIFIED_BUSINESS == roleType)) {
      String otp = mfaService.generateVerificationOtp(6);
      PreVerificationDto preVerification = PreVerificationDto
              .builder()
              .code(otp)
              .phoneNumber(user.getPhoneNumber())
              .emailAddress(dto.getEmailAddress())
              .build();

      initPreVerificationOrAuthentication(preVerification, VerificationType.EMAIL);
      savePreVerificationOtp(user.getUsername(), otp);

      setPreVerificationAuthorities(user, roleType);
      Authentication authenticationToken = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
      accessToken = createAccessToken(user);
      refreshToken = createRefreshToken(user);

      setContext(authenticationToken);
      saveToken(user.getUsername(), accessToken);
      saveRefreshToken(user.getUsername(), refreshToken);

      signInResponse.setNextAuthentication(NextAuthentication.PRE_VERIFICATION);
      signInResponse.setAccessToken(accessToken);
      signInResponse.setRefreshToken(refreshToken);
      signInResponse.setPhoneNumber(user.getPhoneNumber());
      return signInResponse;
    }

    if (user.isMfaEnabled()) {
      if (SMS == user.getMfaType() || EMAIL == user.getMfaType()) {
        String otp = mfaService.generateVerificationOtp(6);
        PreVerificationDto preVerification = PreVerificationDto.builder()
                .code(otp)
                .phoneNumber(user.getPhoneNumber())
                .emailAddress(dto.getEmailAddress())
                .build();

        VerificationType verificationType = VerificationType.valueOf(user.getMfaType().name());
        initPreVerificationOrAuthentication(preVerification, verificationType);
        savePreAuthenticationOtp(user.getUsername(), otp);
      }

      user.setAuthorities(getPreAuthenticatedAuthorities());
      Authentication authenticationToken = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
      accessToken = createAccessToken(user);
      setContext(authenticationToken);
      saveToken(user.getUsername(), accessToken);

      signInResponse.setNextAuthentication(NextAuthentication.MFA);
      signInResponse.setMfaEnabled(true);
      signInResponse.setMfaType(user.getMfaType());
      signInResponse.setAccessToken(accessToken);
      signInResponse.setRefreshToken(null);
      signInResponse.setPhoneNumber(user.getPhoneNumber());
      return signInResponse;
    }

    accessToken = createAccessToken(user);
    refreshToken = createRefreshToken(user);

    saveToken(user.getUsername(), accessToken);
    saveRefreshToken(user.getUsername(), refreshToken);
    setContext(authentication);

    signInResponse.setAccessToken(accessToken);
    signInResponse.setRefreshToken(refreshToken);
    signInResponse.setAuthenticationStatus(COMPLETED);
    return signInResponse;
  }

  /**
   * <p>How the user register on the application or API.</p>
   * <br/>
   *
   * <p>Necessary validation checks for example if the email address or phone number has been used by another user is performed on the dto {@link SignUpDto}
   * and this process is aided by the {@link com.umulam.fleen.health.validator.EmailAddressExists EmailAddressExists} and
   * {@link com.umulam.fleen.health.validator.PhoneNumberExists PhoneNumberExists} annotations.</p>
   * <br/>
   *
   * <p>The {@link ProfileType} will be used to decide what role to associate with the user's profile as a user or professional or business.</p>
   * <br/>
   *
   * <p>The password chosen by the user would be encoded through an algorithm for example using
   * {@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder BCryptPasswordEncoder}.</p>
   * <br/>
   *
   * <p>If the registration completes, a {@link ProfileVerificationHistory} entry is created that informs the user their profile verification is pending. An
   * email message will be sent to the registered email address informing the user of this process and status. By default, a user profile will be
   * 'inactive' during sign up until the registration process has been completed at {@link #completeSignUp(VerificationCodeDto, FleenUser) completeSignUp}.
   * The user will be able to view a history of the stages their profile is currently in and has passed through.</p>
   * <br/>
   *
   * <p>The user will complete the registration process by using a OTP delivered to their phone number or email address through
   * {@link #initPreVerificationOrAuthentication(PreVerificationDto, VerificationType) initPreVerificationOrAuthentication}</p>
   * <br/>
   *
   * @param dto contains basic details that allow a registration on the platform
   * @return {@link SignUpResponse} that contains other details like access token,
   * refresh token and authentication progress that can be used to complete
   * the registration process.
   */
  @Override
  @Transactional
  public SignUpResponse signUp(SignUpDto dto) {
    Member member = dto.toMember();
    ProfileType userType = ProfileType.valueOf(dto.getProfileType());

    Role role = null;
    switch (userType) {
      case USER:
        role = roleService.getRoleByCode(PRE_VERIFIED_USER.name());
        break;

      case PROFESSIONAL:
        role = roleService.getRoleByCode(PRE_VERIFIED_PROFESSIONAL.name());
        break;

      case BUSINESS:
        role = roleService.getRoleByCode(PRE_VERIFIED_BUSINESS.name());
        break;
    }

    member.setPassword(passwordEncoder.encode(member.getPassword().trim()));
    Set<Role> roles = new HashSet<>();
    roles.add(role);
    member.setRoles(roles);

    ProfileVerificationStatus verificationStatus = ProfileVerificationStatus.PENDING;
    MemberStatus memberStatus = memberStatusService.getMemberStatusByCode(MemberStatusType.INACTIVE.name());
    member.setMemberStatus(memberStatus);
    member.setVerificationStatus(verificationStatus);
    member.setUserType(userType);
    memberService.save(member);

    ProfileVerificationMessage verificationMessage = profileVerificationMessageService.getProfileVerificationMessageByType(ProfileVerificationMessageType.PENDING);
    if (Objects.nonNull(verificationMessage)) {
      SaveProfileVerificationMessageRequest verificationMessageRequest = SaveProfileVerificationMessageRequest
              .builder()
              .verificationMessageType(verificationMessage.getVerificationMessageType())
              .verificationStatus(verificationStatus)
              .member(member)
              .emailAddress(member.getEmailAddress())
              .build();

      saveAndSendProfileVerificationMessage(verificationMessageRequest);
    }

    FleenUser user = initAuthentication(member);
    String accessToken = createAccessToken(user);
    String refreshToken = createRefreshToken(user);

    String otp = mfaService.generateVerificationOtp(6);
    PreVerificationDto preVerification = PreVerificationDto.builder()
            .code(otp)
            .phoneNumber(dto.getPhoneNumber())
            .emailAddress(dto.getEmailAddress())
            .build();

    VerificationType verificationType = VerificationType.valueOf(dto.getVerificationType());
    initPreVerificationOrAuthentication(preVerification, verificationType);
    savePreVerificationOtp(user.getUsername(), otp);

    saveToken(user.getUsername(), accessToken);
    saveRefreshToken(user.getUsername(), refreshToken);
    return new SignUpResponse(accessToken, refreshToken, IN_PROGRESS, verificationType);
  }

  /**
   * <p>How the complete the registration process on the application or API.</p>
   * <br/>
   *
   * <p>The OTP sent to the user's email address or phone number will be validated or verified of existence and equality.</p>
   * <br/>
   *
   * <p>If the profile type is {@link ProfileType#USER USER}, the verification is approved automatically because they have a single verification stage.
   * This does not applies to a business or professional because they have to upload other documents like medical certificate, licenses, business registration
   * certificate and in some cases tax identification certificate if deemed necessary.</p>
   * <br/>
   *
   * <p>All profile are set to active using {@link MemberStatusType#ACTIVE Active} if the registration process is completed successfully.</p>
   *
   * @param dto contains verification code to use to complete the user registration process
   * @param fleenUser the authenticated user after the user successfully started the registration or sign-up process.
   * @return {@link SignUpResponse} contains access token and refresh token
   * that can be used to access the application or API feature
   * @throws VerificationFailedException if the user's email address does not exist
   * @throws ExpiredVerificationCodeException if the OTP has already expired and is deleted from the cache at {@link CacheService}
   * @throws InvalidVerificationCodeException if the OTP is not equal to what is currently available in the cache at {@link CacheService}
   * @throws AlreadySignedUpException if the registration process has been completed using this email address
   */
  @Override
  @Transactional
  public SignUpResponse completeSignUp(VerificationCodeDto dto, FleenUser fleenUser) {
    String username = fleenUser.getUsername();
    String verificationKey = getPreVerificationCacheKey(username);
    String code = dto.getCode();

    validateSmsAndEmailMfa(verificationKey, code);

    Member member = memberService.getMemberByEmailAddress(username);
    if (Objects.isNull(member)) {
      throw new VerificationFailedException();
    }

    FleenUser user = initAuthentication(member);
    String accessToken = createAccessToken(user);
    String refreshToken = createRefreshToken(user);

    if (MemberStatusType.ACTIVE.getValue().equals(member.getMemberStatus().getCode())) {
      throw new AlreadySignedUpException();
    }

    Role role = null;
    ProfileVerificationMessage verificationMessage = null;
    ProfileVerificationStatus profileVerificationStatus = null;
    switch (member.getUserType()) {
      case USER:
        role = roleService.getRoleByCode(USER.name());
        profileVerificationStatus = ProfileVerificationStatus.APPROVED;
        member.setVerificationStatus(profileVerificationStatus);
        verificationMessage = profileVerificationMessageService.getProfileVerificationMessageByType(ProfileVerificationMessageType.APPROVED);
        break;

      case PROFESSIONAL:
        role = roleService.getRoleByCode(PRE_APPROVED_PROFESSIONAL.name());
        break;

      case BUSINESS:
        role = roleService.getRoleByCode(PRE_APPROVED_BUSINESS.name());
        break;
    }

    if (Objects.nonNull(verificationMessage) && ProfileType.USER == member.getUserType()) {
      SaveProfileVerificationMessageRequest verificationMessageRequest = SaveProfileVerificationMessageRequest.builder()
              .verificationMessageType(verificationMessage.getVerificationMessageType())
              .verificationStatus(profileVerificationStatus)
              .member(member)
              .emailAddress(member.getEmailAddress())
              .build();

      saveAndSendProfileVerificationMessage(verificationMessageRequest);
    }

    Set<Role> roles = new HashSet<>();
    roles.add(role);
    member.setRoles(roles);

    MemberStatus memberStatus = memberStatusService.getMemberStatusByCode(MemberStatusType.ACTIVE.name());
    member.setMemberStatus(memberStatus);
    memberService.save(member);

    saveToken(user.getUsername(), accessToken);
    saveRefreshToken(user.getEmailAddress(), refreshToken);
    return new SignUpResponse(accessToken, refreshToken, COMPLETED, null);
  }

  /**
   * <p>User can request for a new OTP if they are unable to complete their registration for example because they abandoned the sign-up process midway or because
   * the OTP they want to use to complete the registration at {@link #completeSignUp(VerificationCodeDto, FleenUser) completeSignUp} has expired.</p>
   * <br/>
   *
   * <p>Where the OTP will be sent will be decided by the {@link ResendVerificationCodeDto#getVerificationType() verificationType} and this can be through
   * {@link VerificationType#EMAIL EMAIL} or {@link VerificationType#SMS SMS}.</p>
   * <br/>
   *
   * @param dto contains phone number or email address to send OTP to, to complete the verification process
   * @param user the user wanting to complete the verification process
   * @return {@link FleenHealthResponse} if the code has been sent successfully
   * @throws VerificationFailedException if there's already an existing OTP associated with the user profile
   */
  @Override
  public FleenHealthResponse resendVerificationCode(ResendVerificationCodeDto dto, FleenUser user) {
    String username = user.getUsername();
    String verificationKey = getPreVerificationCacheKey(username);
    if (cacheService.exists(verificationKey)) {
      throw new VerificationFailedException();
    }

    VerificationType verificationType = VerificationType.valueOf(dto.getVerificationType());
    String otp = mfaService.generateVerificationOtp(6);
    PreVerificationDto preVerification = PreVerificationDto.builder()
            .code(otp)
            .phoneNumber(dto.getPhoneNumber())
            .emailAddress(dto.getEmailAddress())
            .build();

    initPreVerificationOrAuthentication(preVerification, verificationType);
    savePreVerificationOtp(user.getUsername(), otp);
    return new FleenHealthResponse(VERIFICATION_CODE_MESSAGE);
  }

  /**
   * Initialize the authentication context with the user details
   * @param member the registered user to associate with the authentication context
   * @return {@link FleenUser} a user that been associated with the authentication context
   */
  public FleenUser initAuthentication(Member member) {
    FleenUser user = FleenUser.fromMember(member);
    Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    setContext(authentication);
    return user;
  }

  /**
   * Remove the user authentication context token from the cache
   * @param username the name of a user associated with the authentication context
   */
  @Override
  public void signOut(String username) {
    String accessAuthKey = getAuthCacheKey(username);
    String refreshAuthKey = getAuthRefreshCacheKey(username);

    if (cacheService.exists(accessAuthKey)) {
      cacheService.delete(accessAuthKey);
    }

    if (cacheService.exists(refreshAuthKey)) {
      cacheService.delete(refreshAuthKey);
    }
    SecurityContextHolder.clearContext();
  }

  /**
   * <p>When a user has {@link MfaType MFA} enabled on their profile, they are required to pass through this process before they are fully authenticated and
   * can access the feature of the application as designated by their roles.</p>
   * <br/>
   *
   * <p>The {@link ConfirmMfaDto#getMfaType() MfaType} will be used to decide what service to validate the OTP or code against. For example, if the user
   * has enabled the {@link MfaType#AUTHENTICATOR Authenticator} type of MFA on their profile, the code will be validated through
   * {@link MfaService#verifyOtp(String, String) verifyOtp} by retrieving the secret associated with the user's profile. If the MFA type is for example
   * {@link MfaType#EMAIL EMAIL}, the code will be validated through {@link #validateSmsAndEmailMfa(String, String) validateSmsAndEmailMfa}.</p>
   * <br/>
   *
   * @param fleenUser The user that has started the sign-in process at {@link #signIn(SignInDto) signIn}.
   * @param dto details including the OTP code and type required to complete the sign-in process
   * @return {@link SignInResponse} if the validation was successful
   * @throws VerificationFailedException if the user's email address does not exist
   * @throws ExpiredVerificationCodeException if the OTP has already expired and is deleted from the cache at {@link CacheService#exists(String) exists}
   * @throws InvalidVerificationCodeException if the OTP is not equal to what is currently available in the cache at {@link CacheService#get(String) get}
   */
  @Override
  public SignInResponse validateMfa(FleenUser fleenUser, ConfirmMfaDto dto) {
    String username = fleenUser.getUsername();
    MfaType mfaType = MfaType.valueOf(dto.getMfaType());
    String code = dto.getCode();

    if (SMS == mfaType || EMAIL == mfaType) {
      String verificationKey = getPreAuthenticationCacheKey(username);
      validateSmsAndEmailMfa(verificationKey, code);
    }
    else if (AUTHENTICATOR == mfaType)  {
      String secret = memberService.getTwoFaSecret(fleenUser.getId());
      boolean valid = mfaService.verifyOtp(code, secret);
      if (!valid) {
        throw new InvalidVerificationCodeException(code);
      }
    }

    Member member = memberService.getMemberByEmailAddress(username);
    if (Objects.isNull(member)) {
      throw new InvalidAuthenticationException(username);
    }

    FleenUser user = initAuthentication(member);
    String accessToken = createAccessToken(user);
    String refreshToken = createRefreshToken(user);

    saveToken(username, accessToken);
    saveRefreshToken(username, refreshToken);
    return SignInResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .authenticationStatus(COMPLETED)
            .build();
  }

  /**
   * <p>Validate a code like OTP by checking if it exists in the DB or cache for example {@link CacheService} and confirm if it is equal to the code saved in
   * the store.</p>
   * <br/>
   *
   * @param verificationKey the key to check for the existence of the verification code and the validity of the code associated with it
   * @param code the code to validate against the code saved and associated with the verification key
   */
  private void validateSmsAndEmailMfa(String verificationKey, String code) {
    if (!cacheService.exists(verificationKey)) {
      throw new ExpiredVerificationCodeException();
    }

    if (!cacheService.get(verificationKey).equals(code)) {
      throw new InvalidVerificationCodeException(code);
    }
  }

  /**
   * <p>Authenticate the user's credentials before granting them access to the application or API features.</p>
   * <br/>
   *
   * @param emailAddress a user identifier to validate against during the authentication process
   * @param password a password to validate and match with the encoded password stored in the DB
   * @return {@link Authentication} after the authentication process is completed
   */
  @Override
  public Authentication authenticate(String emailAddress, String password) {
    Authentication authenticationToken =
            new UsernamePasswordAuthenticationToken(emailAddress.trim().toLowerCase(), password);
    return authenticationManager.authenticate(authenticationToken);
  }

  /**
   * <p>Associate the user's authentication with the Security Context using {@link SecurityContextHolder}</p>
   * <br/>
   *
   * @param authentication the authenticated user
   */
  @Override
  public void setContext(Authentication authentication) {
    if (authentication.isAuthenticated()) {
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
  }

  /**
   * <p>When a user signs up but has not completed the registration process. The user will be required to do so through
   * {@link #completeSignUp(VerificationCodeDto, FleenUser) completeSignUp} and will be associated with appropriate authorities through
   * {@link com.umulam.fleen.health.util.FleenAuthorities FleenAuthorities}</p>
   * <br/>
   *
   * @param user the authenticated user
   * @param roleType the role will be used to decide the authorities to associate with the user during the authentication process
   */
  private void setPreVerificationAuthorities(FleenUser user, RoleType roleType) {
    switch (roleType) {
      case PRE_VERIFIED_USER:
        user.setAuthorities(getUserPreVerifiedAuthorities());
        break;

      case PRE_VERIFIED_PROFESSIONAL:
        user.setAuthorities(getProfessionalPreVerifiedAuthorities());
        break;

      case PRE_VERIFIED_BUSINESS:
        user.setAuthorities(getBusinessPreVerifiedAuthorities());
        break;
    }
  }

  /**
   * <p>Generate a new access token that can be used to access the application or API features.</p>
   * <br/>
   *
   * @param username the user's identifier associated with the access token
   * @return {@link SignInResponse} that contains new access token
   * that the user can use to perform actions on the application
   */
  @Override
  public SignInResponse refreshToken(String username, String token) {
    String verificationKey = getAuthRefreshCacheKey(username);

    if (!cacheService.exists(verificationKey)) {
      throw new InvalidAuthenticationException(username);
    }

    if (!cacheService.get(verificationKey).equals(token)) {
      throw new InvalidAuthenticationException(username);
    }

    signOut(username);
    Member member = memberService.getMemberByEmailAddress(username);
    if (Objects.isNull(member)) {
      throw new InvalidAuthenticationException(username);
    }

    FleenUser user = FleenUser.fromMember(member);
    String accessToken = createAccessToken(user);
    String refreshToken = createRefreshToken(user);
    Authentication authenticationToken = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    setContext(authenticationToken);

    saveToken(username, accessToken);
    saveRefreshToken(username, refreshToken);
    return SignInResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .emailAddress(user.getEmailAddress())
            .phoneNumber(user.getPhoneNumber())
            .authenticationStatus(COMPLETED)
            .nextAuthentication(NextAuthentication.NONE)
            .build();
  }

  /**
   * <p>Create a access token that can be used to perform actions on the application or through the API.</p>
   * <br/>
   *
   * @param user the authenticated user
   * @return the token to use to access the application or API features
   */
  @Override
  public String createAccessToken(FleenUser user) {
    return jwtProvider.generateToken(user, ACCESS_TOKEN);
  }

  /**
   * <p>Create a refresh token that can be used to obtain new access token to perform actions on the application or through the API.</p>
   * <br/>
   *
   * @param user the authenticated user
   * @return the token to use to obtain new access token.
   */
  @Override
  public String createRefreshToken(FleenUser user) {
    return jwtProvider.generateRefreshToken(user, REFRESH_TOKEN);
  }

  /**
   * <p>Save the authentication access token.</p>
   * <br/>
   *
   * @param subject the user's identifier to associate with the access token
   * @param token the user's token to validate during the requests and process of using the application
   */
  @Override
  public void saveToken(String subject, String token) {
    Duration duration = Duration.ofHours(toHours(new Date(), jwtProvider.getExpirationDateFromToken(token)));
    cacheService.set(getAuthCacheKey(subject), token, duration);
  }

  /**
   * <p>Save the authentication refresh token that can only be used once to get a new access token.</p>
   * <br/>
   *
   * @param subject the user's identifier to associate with the refresh token
   * @param token the user's token to validate during request and use to get a new token
   */
  @Override
  public void saveRefreshToken(String subject, String token) {
    Calendar timeout = Calendar.getInstance();
    timeout.setTimeInMillis(System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY);
    Duration duration = Duration.ofHours(toHours(new Date(), timeout.getTime()));
    cacheService.set(getAuthRefreshCacheKey(subject), token, duration);
  }

  /**
   * <p>Save the user's pre-verification otp.</p>
   * <br/>
   *
   * @param username the user's identifier to associate with the pre-verification top or code
   * @param otp a random code associated with the user's identifier during the pre-verification process
   */
  private void savePreVerificationOtp(String username, String otp) {
    cacheService.set(getPreVerificationCacheKey(username), otp, Duration.ofMinutes(1));
  }

  /**
   * <p>Save the user's pre-authentication otp.</p>
   * <br/>
   *
   * @param username the user's identifier to associate with the pre-authentication top or code
   * @param otp a random code associated with the user's identifier during the pre-authentication process
   */
  private void savePreAuthenticationOtp(String username, String otp) {
    cacheService.set(getPreAuthenticationCacheKey(username), otp, Duration.ofMinutes(1));
  }

  /**
   * <p>Prefix a user's identifier with a predefined key used to save an authentication token like JWT.</p>
   * <br/>
   *
   * @param username a user identifier found on the system or is to be registered on the system
   * @return a string concatenation of a predefined prefix and the user's identifier
   */
  public static String getAuthCacheKey(String username) {
    return AUTH_CACHE_PREFIX.concat(username);
  }

  /**
   * <p>Prefix a user's identifier with a predefined key used to save an authentication refresh token like JWT.</p>
   * <br/>
   *
   * @param username a user identifier found on the system or is to be registered on the system
   * @return a string concatenation of a predefined prefix and the user's identifier
   */
  public static String getAuthRefreshCacheKey(String username) {
    return AUTH_REFRESH_CACHE_PREFIX.concat(username);
  }

  /**
   * <p>Prefix a user's identifier with a predefined key used to save a pre-verification token or OTP or code.</p>
   * <br/>
   *
   * @param username a user identifier found on the system or is to be registered on the system
   * @return a string concatenation of a predefined prefix and the user's identifier
   */
  private String getPreVerificationCacheKey(String username) {
    return PRE_VERIFICATION_PREFIX.concat(username);
  }

  /**
   * <p>Prefix a user's identifier with a predefined key used to save a pre-authentication token or OTP or code.</p>
   * <br/>
   *
   * @param username a user identifier found on the system or is to be registered on the system
   * @return a string concatenation of a predefined prefix and the user's identifier
   */
  private String getPreAuthenticationCacheKey(String username) {
    return PRE_AUTHENTICATION_PREFIX.concat(username);
  }

  /**
   * <p>An email message interpolated with data or details for pre-verification purpose sent to user's email address.</p>
   * <br/>
   *
   * @param data contains data like otp to bind with the email template
   * @return an email message that has been prepared from a template and interpolated with all the data required in the message
   */
  private String getPreVerificationTemplate(Map<String, Object> data) {
    return emailService.processAndReturnTemplate(PRE_VERIFICATION_TEMPLATE_NAME, data);
  }

  /**
   * <p>When a user is registering or signing up on the platform, the user is required to complete a verification in which an OTP is sent
   * to the email address or phone number they're planning to use on the platform to confirm their ownership. The OTP will be sent to either of
   * two channels depending on the system or user preference.</p>
   * <br/>
   *
   * @param dto contains details like the email address or phone number to send the OTP code to and the code itself
   *            to send to the email address or phone number
   * @param verificationType contains the type of pre-verification as decided by the system or user and where to send the OTP or code to
   */
  private void initPreVerificationOrAuthentication(PreVerificationDto dto, VerificationType verificationType) {
    if (Objects.requireNonNull(verificationType) == VerificationType.SMS) {
      sendSmsPreVerificationCode(dto);
    } else {
      sendEmailPreVerificationCode(dto);
    }
  }

  /**
   * <p>When a user has not completed the registration or sign-up process, an OTP or random code of a fixed length as defined
   * by the system will be sent to the user's email address which will be use to complete the verification found at
   * {@link #completeSignUp(VerificationCodeDto, FleenUser) completeSignUp} and there exists a similar method but only delivers
   * an SMS message of the OTP to a specified phone number found at {@link #sendSmsPreVerificationCode(PreVerificationDto) sendSmsPreVerificationCode}</p>
   * <br/>
   *
   * @param dto contains details like the email address to send the OTP code to and the code itself to send to the email address
   */
  private void sendEmailPreVerificationCode(PreVerificationDto dto) {
    String emailBody = getPreVerificationTemplate(Map.of(PRE_VERIFICATION_OTP_KEY, dto.getCode()));
    if (emailBody == null) {
      throw new VerificationFailedException();
    }
    EmailDetails emailDetails = EmailDetails.builder()
            .from(EmailMessageSource.BASE.getValue())
            .to(dto.getEmailAddress())
            .subject(PRE_VERIFICATION_EMAIL_SUBJECT)
            .body(emailBody)
            .build();
    emailService.sendHtmlMessage(emailDetails);
  }

  /**
   * <p>Send a message to the user's email address that inform them about the current stage, the state of their profile verification or status.</p>
   * <br/>
   *
   * @param details contains the message either in HTML or plain text and the recipient to send the message to
   */
  private void sendAVerificationEmail(EmailDetails details) {
    emailService.sendHtmlMessage(details);
  }

  private void saveAndSendProfileVerificationMessage(SaveProfileVerificationMessageRequest request) {
    ProfileVerificationMessage verificationMessage = profileVerificationMessageService
            .getProfileVerificationMessageByType(request.getVerificationMessageType());

    if (Objects.nonNull(verificationMessage)) {
      ProfileVerificationHistory history = new ProfileVerificationHistory();
      history.setProfileVerificationStatus(request.getVerificationStatus());
      history.setMember(request.getMember());
      history.setMessage(verificationMessage.getMessage());
      verificationHistoryService.saveVerificationHistory(history);

      EmailDetails emailDetails = EmailDetails.builder()
              .from(EmailMessageSource.BASE.getValue())
              .to(request.getEmailAddress())
              .subject(verificationMessage.getTitle())
              .body(verificationMessage.getHtmlMessage())
              .plainText(verificationMessage.getPlainText())
              .build();
      sendAVerificationEmail(emailDetails);
    }
  }

  /**
   * <p>When a user has not completed the registration or sign-up process, an OTP or random code of a fixed length as defined
   * by the system will be sent to the user's phone number which will be use to complete the verification found at
   * {@link #completeSignUp(VerificationCodeDto, FleenUser) completeSignUp} and there exists a similar method but only delivers
   * an email message of the OTP to a specified email address found at {@link #sendEmailPreVerificationCode(PreVerificationDto) sendEmailPreVerificationCode}</p>
   * <br/>
   *
   * @param dto contains details like the phone number to send the OTP code to and the code itself to send to the phone number
   */
  private void sendSmsPreVerificationCode(PreVerificationDto dto) {
    Optional<SmsMessage> verificationTemplate = mobileTextService.getPreVerificationSmsMessage(MessageType.PRE_VERIFICATION);
    if (verificationTemplate.isEmpty()) {
      throw new VerificationFailedException();
    }
    String verificationMessage = MessageFormat.format(verificationTemplate.get().getMessage(), dto.getCode());
    mobileTextService.sendSms(dto.getPhoneNumber(), verificationMessage);
  }
}
