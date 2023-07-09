package com.umulam.fleen.health.service;

import com.umulam.fleen.health.model.domain.Member;
import com.umulam.fleen.health.model.domain.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationDocumentService {

  Optional<VerificationDocument> getVerificationDocument(Integer verificationDocumentId);

  List<VerificationDocument> getVerificationDocumentsByMember(Member member);
}
