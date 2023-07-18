package com.umulam.fleen.health.service;

import com.umulam.fleen.health.model.request.search.ProfessionalSearchRequest;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.model.view.ProfessionalViewBasic;
import com.umulam.fleen.health.model.view.SearchResultView;

public interface HealthSessionService {

  SearchResultView viewProfessionals(ProfessionalSearchRequest searchRequest);

  ProfessionalViewBasic viewProfessionalDetail(Integer professionalId);

  void bookSession(Object dto, FleenUser user);
}
