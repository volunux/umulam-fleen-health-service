package com.umulam.fleen.health.service;

import com.umulam.fleen.health.constant.verification.ProfileVerificationStatus;
import com.umulam.fleen.health.model.domain.Professional;
import com.umulam.fleen.health.model.dto.professional.UpdateProfessionalDetailsDto;
import com.umulam.fleen.health.model.dto.professional.UploadProfessionalDocumentDto;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.model.view.ProfessionalView;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProfessionalService {

  List<Object> getProfessionalsByAdmin();

  @Transactional
  Professional updateDetails(UpdateProfessionalDetailsDto dto, FleenUser user);

  @Transactional
  void uploadDocuments(UploadProfessionalDocumentDto dto, FleenUser user);

  ProfileVerificationStatus checkVerificationStatus(FleenUser user);

  @Transactional
  void requestForVerification(FleenUser user);

  @Transactional(readOnly = true)
  ProfessionalView toProfessionalView(Professional entry);

  @Transactional(readOnly = true)
  List<ProfessionalView> toProfessionalViews(List<Professional> entries);

  void setVerificationDocument(ProfessionalView businessView);
}