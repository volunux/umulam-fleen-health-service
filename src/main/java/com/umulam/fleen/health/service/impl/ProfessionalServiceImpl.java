package com.umulam.fleen.health.service.impl;

import com.amazonaws.HttpMethod;
import com.umulam.fleen.health.configuration.aws.s3.S3BucketNames;
import com.umulam.fleen.health.constant.base.ProfileType;
import com.umulam.fleen.health.constant.member.ProfessionalTitle;
import com.umulam.fleen.health.constant.professional.AvailabilityDayOfTheWeek;
import com.umulam.fleen.health.constant.professional.ProfessionalAvailabilityStatus;
import com.umulam.fleen.health.constant.verification.ProfileVerificationStatus;
import com.umulam.fleen.health.exception.member.UserNotFoundException;
import com.umulam.fleen.health.exception.professional.*;
import com.umulam.fleen.health.model.domain.*;
import com.umulam.fleen.health.model.dto.professional.UpdateProfessionalAvailabilityDto;
import com.umulam.fleen.health.model.dto.professional.UpdateProfessionalAvailabilityStatusDto;
import com.umulam.fleen.health.model.dto.professional.UpdateProfessionalDetailsDto;
import com.umulam.fleen.health.model.dto.professional.UploadProfessionalDocumentDto;
import com.umulam.fleen.health.model.mapper.ProfessionalAvailabilityMapper;
import com.umulam.fleen.health.model.mapper.ProfessionalMapper;
import com.umulam.fleen.health.model.mapper.VerificationDocumentMapper;
import com.umulam.fleen.health.model.response.professional.GetProfessionalUpdateAvailabilityStatusResponse;
import com.umulam.fleen.health.model.response.professional.GetProfessionalUpdateVerificationDetailResponse;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.model.view.ProfessionalAvailabilityView;
import com.umulam.fleen.health.model.view.VerificationDocumentView;
import com.umulam.fleen.health.model.view.professional.ProfessionalView;
import com.umulam.fleen.health.model.view.professional.ProfessionalViewBasic;
import com.umulam.fleen.health.repository.jpa.ProfessionalAvailabilityJpaRepository;
import com.umulam.fleen.health.repository.jpa.ProfessionalJpaRepository;
import com.umulam.fleen.health.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.umulam.fleen.health.util.DateTimeUtil.toTime;
import static com.umulam.fleen.health.util.EnumUtil.convertEnumToList;

@Slf4j
@Service
@Primary
public class ProfessionalServiceImpl implements ProfessionalService, ProfileService {

  protected final MemberService memberService;
  protected final S3Service s3Service;
  protected final CountryService countryService;
  protected final VerificationDocumentService verificationDocumentService;
  protected final ProfessionalJpaRepository repository;
  protected final ProfessionalAvailabilityJpaRepository professionalAvailabilityJpaRepository;
  protected final S3BucketNames s3BucketNames;

  public ProfessionalServiceImpl(MemberService memberService,
                             S3Service s3Service,
                             CountryService countryService,
                             VerificationDocumentService verificationDocumentService,
                             ProfessionalJpaRepository repository,
                             ProfessionalAvailabilityJpaRepository professionalAvailabilityJpaRepository,
                             S3BucketNames s3BucketNames) {
    this.memberService = memberService;
    this.s3Service = s3Service;
    this.countryService = countryService;
    this.verificationDocumentService = verificationDocumentService;
    this.repository = repository;
    this.professionalAvailabilityJpaRepository = professionalAvailabilityJpaRepository;
    this.s3BucketNames = s3BucketNames;
  }

  @Override
  @Transactional(readOnly = true)
  public ProfessionalView findProfessionalById(Long id) {
    Professional professional = getProfessional(id);
    ProfessionalView view = toProfessionalView(professional);
    setVerificationDocument(view);
    return view;
  }

  @Override
  @Transactional(readOnly = true)
  public ProfessionalViewBasic findProfessionalBasicById(Long id) {
    Professional professional = getProfessional(id);
    return ProfessionalMapper.toProfessionalViewBasic(professional);
  }

  @Override
  @Transactional
  public Professional save(Professional professional) {
    return repository.save(professional);
  }

  @Override
  public List<ProfessionalView> toProfessionalViews(List<Professional> professionals) {
    return ProfessionalMapper.toProfessionalViews(professionals);
  }

  @Override
  public ProfessionalView toProfessionalView(Professional professional) {
    return ProfessionalMapper.toProfessionalView(professional);
  }

  @Override
  @Transactional(readOnly = true)
  public Professional getDetails(FleenUser user) {
    getMember(user.getEmailAddress());
    Optional<Professional> professionalExists = repository.findProfessionalByEmailAddress(user.getEmailAddress());
    return professionalExists.orElseThrow(HasNoProfessionalProfileException::new);
  }

  @Override
  @Transactional
  public Professional updateDetails(UpdateProfessionalDetailsDto dto, FleenUser user) {
    Professional professional = dto.toProfessional();
    Member member = getMember(user.getEmailAddress());

    Country country;
    Optional<Professional> professionalExists = repository.findProfessionalByEmailAddress(user.getEmailAddress());
    if (professionalExists.isPresent()) {
      Professional existingProfessional = professionalExists.get();
      country = existingProfessional.getCountry();
      professional.setId(existingProfessional.getId());
      professional.setCreatedOn(existingProfessional.getCreatedOn());
    } else {
      country = countryService.getCountry(professional.getCountry().getId());
    }

    professional.setMember(member);
    professional.setCountry(country);

    return repository.save(professional);
  }

  @Override
  public List<VerificationDocumentView> getUploadDocuments(FleenUser user) {
    List<VerificationDocument> verificationDocuments = verificationDocumentService.getByMemberEmailAddress(user.getEmailAddress());
    return VerificationDocumentMapper.toVerificationDocumentViews(verificationDocuments);
  }

  @Override
  @Transactional
  public void uploadDocuments(UploadProfessionalDocumentDto dto, FleenUser user) {
    saveVerificationDocument(user, dto.toUpdateVerificationDocumentRequest());
    Member member = getMember(user.getEmailAddress());
    if (member.getVerificationStatus() == ProfileVerificationStatus.APPROVED) {
      member.setVerificationStatus(ProfileVerificationStatus.IN_PROGRESS);
      memberService.save(member);
    }
  }

  @Override
  @Transactional
  public void requestForVerification(FleenUser user) {
    Member member = getMember(user.getEmailAddress());
    if (member.getVerificationStatus() == ProfileVerificationStatus.IN_PROGRESS
        || member.getVerificationStatus() == ProfileVerificationStatus.APPROVED) {
      return;
    }
    member.setVerificationStatus(ProfileVerificationStatus.IN_PROGRESS);
    memberService.save(member);
  }

  @Override
  public ProfileVerificationStatus checkVerificationStatus(FleenUser user) {
    getMember(user.getEmailAddress());
    return memberService.getVerificationStatus(user.getId());
  }

  @Override
  public GetProfessionalUpdateAvailabilityStatusResponse getProfessionalAvailabilityStatus(FleenUser user) {
    Member member = getMember(user.getEmailAddress());
    Optional<Professional> professionalExists = repository.findProfessionalByEmailAddress(member.getEmailAddress());
    if (professionalExists.isPresent()) {
      Professional professional = professionalExists.get();
      return repository.getProfessionalAvailabilityStatus(professional.getId());
    }
    throw new ProfessionalNotFoundException(user.getEmailAddress());
  }

  @Override
  @Transactional
  public void updateAvailabilityStatus(UpdateProfessionalAvailabilityStatusDto dto, FleenUser user) {
    Member member = getMember(user.getEmailAddress());

    if (member.getVerificationStatus() != ProfileVerificationStatus.APPROVED) {
      throw new ProfessionalProfileNotApproved();
    }

    ProfessionalAvailabilityStatus status = ProfessionalAvailabilityStatus.valueOf(dto.getAvailabilityStatus());
    if (status == ProfessionalAvailabilityStatus.AVAILABLE) {
      long professionalHasAtLeastAvailabilityPeriod = professionalAvailabilityJpaRepository.countByMember(member);
      if (professionalHasAtLeastAvailabilityPeriod < 1) {
        throw new ProfessionalShouldHaveAtLeastOneAvailabilityPeriod();
      }
    }

    Optional<Professional> professionalExists = repository.findProfessionalByEmailAddress(member.getEmailAddress());
    if (professionalExists.isPresent()) {
      Professional professional = professionalExists.get();
      professional.setAvailabilityStatus(status);
      save(professional);
      return;
    }
    throw new ProfessionalNotFoundException(user.getEmailAddress());
  }

  @Override
  public void setVerificationDocument(ProfessionalView professionalView) {
    String emailAddress = professionalView.getMember().getEmailAddress();
    List<VerificationDocument> verificationDocuments = verificationDocumentService.getByMemberEmailAddress(emailAddress);
    List<VerificationDocumentView> verificationDocumentViews = VerificationDocumentMapper.toVerificationDocumentViews(verificationDocuments);
    generateVerificationDocumentSignedUrl(verificationDocumentViews);
    professionalView.setVerificationDocuments(verificationDocumentViews);
  }

  private Member getMember(String emailAddress) {
    Member member = memberService.getMemberByEmailAddress(emailAddress);
    if (Objects.isNull(member)) {
      throw new UserNotFoundException(emailAddress);
    }
    return member;
  }

  @Override
  public Professional getProfessional(Long id) {
    Optional<Professional> professionalExists = repository.findById(id);
    if (professionalExists.isEmpty()) {
      throw new ProfessionalNotFoundException(id);
    }
    return professionalExists.get();
  }

  @Override
  public GetProfessionalUpdateVerificationDetailResponse getUpdateVerificationDetail(FleenUser user) {
    getMember(user.getEmailAddress());
    Optional<Professional> professionalExists = repository.findProfessionalByEmailAddress(user.getEmailAddress());
    GetProfessionalUpdateVerificationDetailResponse updateVerificationDetailResponse = GetProfessionalUpdateVerificationDetailResponse.builder().build();

    if (professionalExists.isPresent()) {
      Professional professional = professionalExists.get();
      updateVerificationDetailResponse.setTitle(professional.getTitle().name());
      updateVerificationDetailResponse.setYearsOfExperience(professional.getYearsOfExperience());
      updateVerificationDetailResponse.setAreaOfExpertise(professional.getAreaOfExpertise());
      updateVerificationDetailResponse.setLanguagesSpoken(professional.getLanguagesSpoken());
      updateVerificationDetailResponse.setProfessionalType(professional.getProfessionalType().name());
      updateVerificationDetailResponse.setQualificationType(professional.getQualificationType().name());
      updateVerificationDetailResponse.setCountry(professional.getCountry().getId().toString());
    }

    List<?> countries = countryService.getCountriesFromCache();
    updateVerificationDetailResponse.setCountries(countries);
    updateVerificationDetailResponse.setProfessionalTitles(convertEnumToList(ProfessionalTitle.class));
    return updateVerificationDetailResponse;
  }

  @Override
  @Transactional
  public void updateAvailabilityOrSchedule(UpdateProfessionalAvailabilityDto dto, FleenUser user) {
    Member member = memberService.getMemberById(user.getId());

    if (member.getUserType() != ProfileType.PROFESSIONAL) {
      throw new NotAProfessionalException(member.getId());
    }

    if (member.getVerificationStatus() != ProfileVerificationStatus.APPROVED) {
      throw new ProfessionalProfileNotApproved();
    }

    List<ProfessionalAvailability> availabilityPeriods = dto.getPeriods()
      .stream()
      .map(period -> ProfessionalAvailability.builder()
        .member(member)
        .startTime(toTime(period.getStartTime()))
        .endTime(toTime(period.getEndTime()))
        .dayOfWeek(AvailabilityDayOfTheWeek.valueOf(period.getDayOfTheWeek()))
        .build()).collect(Collectors.toList());

      professionalAvailabilityJpaRepository.deleteAllByMember(member);
      professionalAvailabilityJpaRepository.saveAll(availabilityPeriods);
  }

  @Override
  public List<ProfessionalAvailabilityView> getUpdateAvailabilityOrSchedule(FleenUser user) {
    Member member = memberService.getMemberById(user.getId());

    if (member.getUserType() != ProfileType.PROFESSIONAL) {
      throw new NotAProfessionalException(member.getId());
    }
    List<ProfessionalAvailability> availabilities = professionalAvailabilityJpaRepository.findAllByMember(member);
    return ProfessionalAvailabilityMapper.toProfessionalAvailabilityViews(availabilities);
  }

  @Override
  public Optional<Professional> findProfessionalByMember(Member member) {
    return repository.findByMember(member);
  }

  @Override
  public List<Professional> findProfessionalsById(List<Long> ids) {
    return repository.findProfessionalsByIds(ids);
  }

  @Override
  public S3Service getS3Service() {
    return s3Service;
  }

  @Override
  public MemberService getMemberService() {
    return memberService;
  }

  @Override
  public VerificationDocumentService getVerificationDocumentService() {
    return verificationDocumentService;
  }

  @Override
  public S3BucketNames getS3BucketNames() {
    return s3BucketNames;
  }

  @Override
  public Double getProfessionalPrice(Long memberId) {
    return repository.findProfessionalPrice(memberId);
  }
}
