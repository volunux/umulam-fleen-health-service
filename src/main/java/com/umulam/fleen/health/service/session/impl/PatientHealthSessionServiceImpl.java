package com.umulam.fleen.health.service.session.impl;

import com.umulam.fleen.health.constant.base.ProfileType;
import com.umulam.fleen.health.exception.healthsession.HealthSessionNotFoundException;
import com.umulam.fleen.health.model.domain.HealthSession;
import com.umulam.fleen.health.model.domain.HealthSessionReview;
import com.umulam.fleen.health.model.domain.Professional;
import com.umulam.fleen.health.model.mapper.HealthSessionReviewMapper;
import com.umulam.fleen.health.model.request.search.base.SearchRequest;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.model.view.healthsession.HealthSessionReviewView;
import com.umulam.fleen.health.model.view.healthsession.HealthSessionView;
import com.umulam.fleen.health.model.view.healthsession.HealthSessionViewBasic;
import com.umulam.fleen.health.model.view.professional.ProfessionalView;
import com.umulam.fleen.health.model.view.search.SearchResultView;
import com.umulam.fleen.health.repository.jpa.HealthSessionJpaRepository;
import com.umulam.fleen.health.repository.jpa.HealthSessionReviewJpaRepository;
import com.umulam.fleen.health.service.ProfessionalService;
import com.umulam.fleen.health.service.session.PatientHealthSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.umulam.fleen.health.model.mapper.HealthSessionMapper.toHealthSessionView;
import static com.umulam.fleen.health.model.mapper.HealthSessionMapper.toHealthSessionViewBasic;
import static com.umulam.fleen.health.model.mapper.ProfessionalMapper.toProfessionalView;
import static com.umulam.fleen.health.model.mapper.ProfessionalMapper.toProfessionalViews;
import static com.umulam.fleen.health.util.FleenHealthUtil.areNotEmpty;
import static com.umulam.fleen.health.util.FleenHealthUtil.toSearchResult;

@Slf4j
@Service
public class PatientHealthSessionServiceImpl implements PatientHealthSessionService {

  private final HealthSessionJpaRepository healthSessionJpaRepository;
  private final ProfessionalService professionalService;
  private final HealthSessionReviewJpaRepository healthSessionReviewJpaRepository;

  public PatientHealthSessionServiceImpl(HealthSessionJpaRepository healthSessionJpaRepository,
                                         ProfessionalService professionalService,
                                         HealthSessionReviewJpaRepository healthSessionReviewJpaRepository) {
    this.healthSessionJpaRepository = healthSessionJpaRepository;
    this.professionalService = professionalService;
    this.healthSessionReviewJpaRepository = healthSessionReviewJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public SearchResultView viewSessions(FleenUser user, SearchRequest req) {
    Page<HealthSession> page;

    if (areNotEmpty(req.getStartDate(), req.getEndDate())) {
      page = healthSessionJpaRepository.findSessionsByUserAndDateBetween(user.getId(), ProfileType.USER, req.getStartDate().atStartOfDay(), req.getEndDate().atStartOfDay(), req.getPage());
    } else {
      page = healthSessionJpaRepository.findSessionsByUser(user.getId(), ProfileType.USER, req.getPage());
    }

    List<HealthSessionViewBasic> views = toHealthSessionViewBasic(page.getContent());
    return toSearchResult(views, page);
  }

  @Override
  @Transactional(readOnly = true)
  public HealthSessionView viewSessionDetail(FleenUser user, Integer healthSessionId) {
    Optional<HealthSession> healthSessionExist = healthSessionJpaRepository.findSessionByUser(user.getId(), ProfileType.USER, healthSessionId);
    if (healthSessionExist.isPresent()) {
      return toHealthSessionView(healthSessionExist.get());
    }
    throw new HealthSessionNotFoundException(healthSessionId);
  }

  @Override
  public List<ProfessionalView> viewProfessionalsOfPatient(FleenUser user) {
    List<Long> professionalsIds = healthSessionJpaRepository.findAllProfessionalsIdsOfUser(user.getId());
    List<Professional> professionals = professionalService.findProfessionalsById(professionalsIds);
    return toProfessionalViews(professionals);
  }

  @Override
  public ProfessionalView viewProfessionalDetail(Integer id) {
    Professional professional = professionalService.getProfessional(id);
    return toProfessionalView(professional);
  }

  @Override
  public List<HealthSessionReviewView> viewReviews(FleenUser user) {
    List<HealthSessionReview> sessionReviews = healthSessionReviewJpaRepository.findPatientReviews(user.getId());
    return HealthSessionReviewMapper.toHealthSessionReviewViews(sessionReviews);
  }
}