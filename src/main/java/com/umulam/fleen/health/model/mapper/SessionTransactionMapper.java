package com.umulam.fleen.health.model.mapper;

import com.umulam.fleen.health.model.domain.transaction.SessionTransaction;
import com.umulam.fleen.health.model.view.transaction.SessionTransactionView;
import com.umulam.fleen.health.model.view.transaction.SessionTransactionViewBasic;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

public class SessionTransactionMapper {

  private SessionTransactionMapper() { }

  public static SessionTransactionView toSessionTransactionView(SessionTransaction entry) {
    if (nonNull(entry)) {
      return SessionTransactionView.builder()
        .id(entry.getId())
        .reference(entry.getReference())
        .status(entry.getStatus().name())
        .paymentGateway(entry.getGateway().name())
        .type(entry.getType().name())
        .subType(entry.getSubType().name())
        .amount(entry.getAmount())
        .currency(entry.getCurrency())
        .sessionReference(entry.getSessionReference())
        .externalReference(entry.getExternalSystemReference())
        .createdOn(entry.getCreatedOn())
        .updatedOn(entry.getUpdatedOn())
        .build();
    }
    return null;
  }

  public static SessionTransactionViewBasic toSessionTransactionViewBasic(SessionTransaction entry) {
    if (nonNull(entry)) {
      return SessionTransactionViewBasic.builder()
        .id(entry.getId())
        .reference(entry.getReference())
        .status(entry.getStatus().name())
        .sessionReference(entry.getSessionReference())
        .amount(entry.getAmount())
        .createdOn(entry.getCreatedOn())
        .currency(entry.getCurrency())
        .build();
    }
    return null;
  }

  public static List<SessionTransactionView> toSessionTransactionViews(List<SessionTransaction> entries) {
    if (nonNull(entries) && !entries.isEmpty()) {
      return entries
              .stream()
              .filter(Objects::nonNull)
              .map(SessionTransactionMapper::toSessionTransactionView)
              .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  public static List<SessionTransactionViewBasic> toSessionTransactionViewBasic(List<SessionTransaction> entries) {
    if (nonNull(entries) && !entries.isEmpty()) {
      return entries
        .stream()
        .filter(Objects::nonNull)
        .map(SessionTransactionMapper::toSessionTransactionViewBasic)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }
}
