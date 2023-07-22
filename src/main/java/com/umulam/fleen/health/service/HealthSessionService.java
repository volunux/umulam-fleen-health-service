package com.umulam.fleen.health.service;

import com.umulam.fleen.health.model.dto.healthsession.BookHealthSessionDto;
import com.umulam.fleen.health.model.dto.healthsession.ReScheduleHealthSessionDto;
import com.umulam.fleen.health.model.request.search.ProfessionalSearchRequest;
import com.umulam.fleen.health.model.response.professional.GetProfessionalBookSessionResponse;
import com.umulam.fleen.health.model.response.professional.ProfessionalCheckAvailabilityResponse;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.model.view.search.ProfessionalViewBasic;
import com.umulam.fleen.health.model.view.search.SearchResultView;
import org.springframework.transaction.annotation.Transactional;

public interface HealthSessionService {

  SearchResultView viewProfessionals(ProfessionalSearchRequest searchRequest);

  ProfessionalViewBasic viewProfessionalDetail(Integer professionalId);

  void bookSession(BookHealthSessionDto dto, FleenUser user);

  void validateAndCompleteTransaction(String body);

  @Transactional
  void cancelSession(Integer sessionId, FleenUser user);

  @Transactional(readOnly = true)
  ProfessionalCheckAvailabilityResponse viewProfessionalAvailability(FleenUser user, Integer professionalId);

  GetProfessionalBookSessionResponse getProfessionalBookSession(FleenUser user, Integer professionalId);

  @Transactional
  Object rescheduleSession(ReScheduleHealthSessionDto dto, FleenUser user, Integer healthSessionId);
}
