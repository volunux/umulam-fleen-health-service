package com.umulam.fleen.health.service.impl;

import com.umulam.fleen.health.constant.professional.ProfessionalAvailabilityStatus;
import com.umulam.fleen.health.constant.verification.ProfileVerificationStatus;
import com.umulam.fleen.health.model.domain.HealthSession;
import com.umulam.fleen.health.model.domain.Member;
import com.umulam.fleen.health.model.domain.Professional;
import com.umulam.fleen.health.model.dto.healthsession.BookHealthSessionDto;
import com.umulam.fleen.health.model.mapper.ProfessionalMapper;
import com.umulam.fleen.health.model.request.search.ProfessionalSearchRequest;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.model.view.search.ProfessionalViewBasic;
import com.umulam.fleen.health.model.view.search.SearchResultView;
import com.umulam.fleen.health.repository.jpa.HealthSessionJpaRepository;
import com.umulam.fleen.health.repository.jpa.HealthSessionProfessionalJpaRepository;
import com.umulam.fleen.health.service.HealthSessionService;
import com.umulam.fleen.health.service.ProfessionalService;
import com.umulam.fleen.health.util.UniqueReferenceGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static com.umulam.fleen.health.constant.base.FleenHealthConstant.REFERENCE_PREFIX;
import static com.umulam.fleen.health.util.FleenHealthUtil.areNotEmpty;
import static com.umulam.fleen.health.util.FleenHealthUtil.toSearchResult;
import static java.util.Objects.nonNull;

@Slf4j
@Service
public class HealthSessionServiceImpl implements HealthSessionService {
  
  private final HealthSessionProfessionalJpaRepository sessionProfessionalJpaRepository;
  private final HealthSessionJpaRepository sessionJpaRepository;
  private final ProfessionalService professionalService;
  private final UniqueReferenceGenerator referenceGenerator;

  public HealthSessionServiceImpl(
          HealthSessionProfessionalJpaRepository sessionProfessionalJpaRepository,
          HealthSessionJpaRepository sessionJpaRepository,
          ProfessionalService professionalService,
          UniqueReferenceGenerator referenceGenerator) {
    this.sessionProfessionalJpaRepository = sessionProfessionalJpaRepository;
    this.sessionJpaRepository = sessionJpaRepository;
    this.professionalService = professionalService;
    this.referenceGenerator = referenceGenerator;
  }

  @Override
  @Transactional(readOnly = true)
  public SearchResultView viewProfessionals(ProfessionalSearchRequest req) {
    Page<Professional> page;
    ProfessionalAvailabilityStatus availability = ProfessionalAvailabilityStatus.AVAILABLE;
    ProfileVerificationStatus verificationStatus = ProfileVerificationStatus.APPROVED;

    if (areNotEmpty(req.getFirstName(), req.getLastName())) {
      page = sessionProfessionalJpaRepository.findByFirstNameAndLastName(req.getFirstName(), req.getLastName(), availability, verificationStatus, req.getPage());
    } else if (nonNull(req.getProfessionalType())) {
      page = sessionProfessionalJpaRepository.findByProfessionalType(req.getProfessionalType(), availability, verificationStatus, req.getPage());
    } else if (nonNull(req.getQualificationType())) {
      page = sessionProfessionalJpaRepository.findByQualification(req.getQualificationType(), availability, verificationStatus, req.getPage());
    } else if (nonNull(req.getLanguageSpoken())) {
      page = sessionProfessionalJpaRepository.findByLanguageSpoken(req.getLanguageSpoken(), availability, verificationStatus, req.getPage());
    } else {
      page = sessionProfessionalJpaRepository.findByAvailabilityStatus(availability, verificationStatus, req.getPage());
    }

    List<ProfessionalViewBasic> views = ProfessionalMapper.toProfessionalViewsBasic(Collections.shuffle(page.getContent()));
    return toSearchResult(views, page);
  }

  @Override
  @Transactional(readOnly = true)
  public ProfessionalViewBasic viewProfessionalDetail(Integer professionalId) {
    return professionalService.findProfessionalBasicById(professionalId);
  }

  @Override
  @Transactional
  public void bookSession(BookHealthSessionDto dto, FleenUser user) {
    HealthSession healthSession = dto.toHealthSession();
    healthSession.setReference(generateSessionReference());
    Member member = Member.builder()
        .id(user.getId())
        .build();
    healthSession.setPatient(member);
    sessionJpaRepository.save(healthSession);
  }

  private String generateSessionReference() {
    return REFERENCE_PREFIX.concat(referenceGenerator.generateUniqueReference());
  }
}
