package com.umulam.fleen.health.service.admin.impl;

import com.umulam.fleen.health.configuration.aws.s3.S3BucketNames;
import com.umulam.fleen.health.constant.verification.ProfileVerificationStatus;
import com.umulam.fleen.health.exception.professional.ProfessionalNotFoundException;
import com.umulam.fleen.health.model.domain.Member;
import com.umulam.fleen.health.model.domain.Professional;
import com.umulam.fleen.health.model.domain.ProfileVerificationMessage;
import com.umulam.fleen.health.model.dto.professional.UpdateProfessionalDetailsDto;
import com.umulam.fleen.health.model.dto.verification.UpdateProfileVerificationStatusDto;
import com.umulam.fleen.health.model.mapper.ProfessionalMapper;
import com.umulam.fleen.health.model.request.SaveProfileVerificationMessageRequest;
import com.umulam.fleen.health.model.request.search.ProfessionalSearchRequest;
import com.umulam.fleen.health.model.view.professional.ProfessionalView;
import com.umulam.fleen.health.model.view.search.SearchResultView;
import com.umulam.fleen.health.repository.jpa.ProfessionalAvailabilityJpaRepository;
import com.umulam.fleen.health.repository.jpa.ProfessionalJpaRepository;
import com.umulam.fleen.health.service.*;
import com.umulam.fleen.health.service.admin.AdminProfessionalService;
import com.umulam.fleen.health.service.external.aws.EmailServiceImpl;
import com.umulam.fleen.health.service.external.aws.MobileTextService;
import com.umulam.fleen.health.service.impl.CacheService;
import com.umulam.fleen.health.service.impl.ProfessionalServiceImpl;
import com.umulam.fleen.health.service.impl.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.umulam.fleen.health.util.FleenHealthUtil.areNotEmpty;
import static com.umulam.fleen.health.util.FleenHealthUtil.toSearchResult;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@Qualifier("adminProfessionalService")
public class AdminProfessionalServiceImpl extends ProfessionalServiceImpl implements AdminProfessionalService, CommonAuthAndVerificationService {

  private final ProfileVerificationMessageService verificationMessageService;
  private final CacheService cacheService;
  private final MobileTextService mobileTextService;
  private final EmailServiceImpl emailService;
  private final VerificationHistoryService verificationHistoryService;
  private final ProfileVerificationMessageService profileVerificationMessageService;

  public AdminProfessionalServiceImpl(MemberService memberService,
                                      S3Service s3Service,
                                      CountryService countryService,
                                      VerificationDocumentService verificationDocumentService,
                                      ProfessionalJpaRepository repository,
                                      CacheService cacheService,
                                      MobileTextService mobileTextService,
                                      EmailServiceImpl emailService,
                                      VerificationHistoryService verificationHistoryService,
                                      ProfileVerificationMessageService verificationMessageService,
                                      ProfessionalAvailabilityJpaRepository professionalAvailabilityJpaRepository,
                                      S3BucketNames s3BucketNames) {
    super(memberService, s3Service, countryService, verificationDocumentService, repository, professionalAvailabilityJpaRepository, s3BucketNames);
    this.verificationMessageService = verificationMessageService;
    this.cacheService = cacheService;
    this.mobileTextService = mobileTextService;
    this.emailService = emailService;
    this.verificationHistoryService = verificationHistoryService;
    this.profileVerificationMessageService = verificationMessageService;
  }


  @Override
  @Transactional(readOnly = true)
  public SearchResultView findProfessionals(ProfessionalSearchRequest req) {
    Page<Professional> page;

    if (areNotEmpty(req.getStartDate(), req.getEndDate())) {
      page = repository.findByDateBetween(req.getStartDate().atStartOfDay(), req.getEndDate().atStartOfDay(), req.getPage());
    } else if (areNotEmpty(req.getFirstName(), req.getLastName())) {
      page = repository.findByFirstNameAndLastName(req.getFirstName(), req.getLastName(), req.getPage());
    } else if (nonNull(req.getAvailabilityStatus())) {
      page = repository.findByAvailabilityStatus(req.getAvailabilityStatus(), req.getPage());
    } else if (nonNull(req.getProfessionalType())) {
      page = repository.findByProfessionalType(req.getProfessionalType(), req.getPage());
    } else if (nonNull(req.getQualificationType())) {
      page = repository.findByQualification(req.getQualificationType(), req.getPage());
    } else if (nonNull(req.getLanguageSpoken())) {
      page = repository.findByLanguageSpoken(req.getLanguageSpoken(), req.getPage());
    } else if (nonNull(req.getVerificationStatus())) {
      page = repository.findByVerificationStatus(req.getVerificationStatus(), req.getPage());
    } else if (nonNull(req.getBeforeDate())) {
      page = repository.findByCreatedOnBefore(req.getBeforeDate().atStartOfDay(), req.getPage());
    } else if (nonNull(req.getAfterDate())) {
      page = repository.findByCreatedOnAfter(req.getAfterDate().atStartOfDay(), req.getPage());
    } else {
      page = repository.findAll(req.getPage());
    }

    List<ProfessionalView> views = ProfessionalMapper.toProfessionalViews(page.getContent());
    return toSearchResult(views, page);
  }

  @Override
  @Transactional(readOnly = true)
  public SearchResultView findProfessionalsByVerificationStatus(ProfessionalSearchRequest req) {
    Page<Professional> page;
    ProfileVerificationStatus verificationStatus = ProfileVerificationStatus.PENDING;
    if (areNotEmpty(req.getStartDate(), req.getEndDate())) {
      page = repository.findByVerificationStatusBetween(verificationStatus, req.getStartDate().atStartOfDay(), req.getEndDate().atStartOfDay(), req.getPage());
    } else if (areNotEmpty(req.getFirstName(), req.getLastName())) {
      page = repository.findByVerificationStatusAndFirstOrLastName(verificationStatus, req.getFirstName(), req.getLastName(), req.getPage());
    } else {
      page = repository.findByVerificationStatus(verificationStatus, req.getPage());
    }

    List<ProfessionalView> views = ProfessionalMapper.toProfessionalViews(page.getContent());
    return toSearchResult(views, page);
  }

  @Override
  @Transactional
  public ProfessionalView updateProfessionalDetail(UpdateProfessionalDetailsDto dto, Long professionalId) {
    Professional professional = dto.toProfessional();

    Optional<Professional> professionalExists = repository.findById(professionalId);
    if (professionalExists.isEmpty()) {
      throw new ProfessionalNotFoundException(professionalId);
    }

    Professional existingProfessional = professionalExists.get();
    professional.setId(existingProfessional.getId());
    professional.setMember(existingProfessional.getMember());

    Professional savedProfessional = repository.save(professional);
    return toProfessionalView(savedProfessional);
  }

  @Override
  @Transactional
  public void updateProfessionalVerificationStatus(UpdateProfileVerificationStatusDto dto, Long professionalId) {
    ProfileVerificationMessage verificationMessage = verificationMessageService.getProfileVerificationMessageFromCache(Long.parseLong(dto.getVerificationMessageTemplateId()));
    Optional<Professional> professionalExists = repository.findById(professionalId);

    if (professionalExists.isEmpty()) {
      throw new ProfessionalNotFoundException(professionalId);
    }

    Professional professional = professionalExists.get();
    Member member = professional.getMember();
    ProfileVerificationStatus verificationStatus = ProfileVerificationStatus.valueOf(dto.getVerificationStatus());
    if (Objects.nonNull(verificationMessage)) {
      SaveProfileVerificationMessageRequest verificationMessageRequest = SaveProfileVerificationMessageRequest.builder()
              .verificationMessageType(verificationMessage.getVerificationMessageType())
              .verificationStatus(verificationStatus)
              .member(member)
              .emailAddress(member.getEmailAddress())
              .comment(dto.getComment())
              .build();

      saveProfileVerificationHistory(verificationMessage, verificationMessageRequest);
      sendProfileVerificationMessage(member.getEmailAddress(), verificationMessage);
    }
    member.setVerificationStatus(verificationStatus);
    save(professional);
  }

  @Override
  public MobileTextService getMobileTextService() {
    return mobileTextService;
  }

  @Override
  public EmailServiceImpl getEmailService() {
    return emailService;
  }

  @Override
  public CacheService getCacheService() {
    return cacheService;
  }

  @Override
  public ProfileVerificationMessageService getProfileVerificationMessageService() {
    return profileVerificationMessageService;
  }

  @Override
  public VerificationHistoryService getVerificationHistoryService() {
    return verificationHistoryService;
  }

  @Override
  public MemberStatusService getMemberStatusService() {
    return null;
  }

}
