package com.umulam.fleen.health.service;

import com.umulam.fleen.health.constant.verification.ProfileVerificationMessageType;
import com.umulam.fleen.health.model.domain.ProfileVerificationMessage;
import com.umulam.fleen.health.model.dto.profileverificationmessage.ProfileVerificationMessageDto;
import com.umulam.fleen.health.model.request.search.ProfessionalSearchRequest;
import com.umulam.fleen.health.model.request.search.ProfileVerificationMessageSearchRequest;
import com.umulam.fleen.health.model.response.other.DeleteIdsDto;
import com.umulam.fleen.health.model.response.profileverificationmessage.GetProfileVerificationMessages;
import com.umulam.fleen.health.model.view.ProfileVerificationMessageView;
import com.umulam.fleen.health.model.view.SearchResultView;

import java.util.List;

public interface ProfileVerificationMessageService {

  ProfileVerificationMessage getProfileVerificationMessageByType(ProfileVerificationMessageType messageType);

  boolean existsById(Integer id);

  void saveProfileVerificationVerificationMessageToCache(Integer messageId, ProfileVerificationMessage profileVerificationMessage);

  ProfileVerificationMessage getProfileVerificationMessageFromCache(Integer messageId);

  List<GetProfileVerificationMessages> getBasicDetails();

  void saveProfileVerificationMessage(ProfileVerificationMessageDto dto);

  void updateProfileVerificationMessage(Integer id, ProfileVerificationMessageDto dto);

  void deleteMany(DeleteIdsDto dto);

  SearchResultView findProfileVerificationMessages(ProfileVerificationMessageSearchRequest searchRequest);

  ProfileVerificationMessageView getById(Integer profileVerificationMessageId);
}
