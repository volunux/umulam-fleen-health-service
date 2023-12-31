package com.umulam.fleen.health.repository.jpa;

import com.umulam.fleen.health.model.domain.Member;
import com.umulam.fleen.health.model.domain.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VerificationDocumentJpaRepository extends JpaRepository<VerificationDocument, Long> {

  @Query("SELECT vd FROM VerificationDocument vd WHERE vd.member.emailAddress = :emailAddress")
  Optional<VerificationDocument> findVerificationDocumentByEmailAddress(String emailAddress);

  @Query("SELECT vd FROM VerificationDocument vd WHERE vd.member.emailAddress = :emailAddress")
  List<VerificationDocument> findVerificationDocumentsByEmailAddress(String emailAddress);

  List<VerificationDocument> findVerificationDocumentsByMember(Member member);

  VerificationDocument findVerificationDocumentByMember(Member member);
}
