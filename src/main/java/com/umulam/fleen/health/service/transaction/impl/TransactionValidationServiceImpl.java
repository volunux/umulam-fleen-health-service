package com.umulam.fleen.health.service.transaction.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umulam.fleen.health.constant.authentication.PaymentGatewayType;
import com.umulam.fleen.health.constant.externalsystem.flutterwave.FlutterwaveWebhookEventType;
import com.umulam.fleen.health.constant.externalsystem.paystack.PaystackWebhookEventType;
import com.umulam.fleen.health.constant.session.*;
import com.umulam.fleen.health.event.CreateSessionMeetingEvent;
import com.umulam.fleen.health.event.CreateSessionMeetingEvents;
import com.umulam.fleen.health.model.domain.HealthSession;
import com.umulam.fleen.health.model.domain.Member;
import com.umulam.fleen.health.model.domain.transaction.SessionTransaction;
import com.umulam.fleen.health.model.domain.transaction.WithdrawalTransaction;
import com.umulam.fleen.health.model.event.base.InternalPaymentValidation;
import com.umulam.fleen.health.model.event.base.WithdrawalTransferValidation;
import com.umulam.fleen.health.model.event.flutterwave.base.FlutterwaveWebhookEvent;
import com.umulam.fleen.health.model.event.paystack.base.PaystackWebhookEvent;
import com.umulam.fleen.health.repository.jpa.HealthSessionJpaRepository;
import com.umulam.fleen.health.repository.jpa.transaction.SessionTransactionJpaRepository;
import com.umulam.fleen.health.repository.jpa.transaction.WithdrawalTransactionJpaRepository;
import com.umulam.fleen.health.service.BankingService;
import com.umulam.fleen.health.service.EarningsService;
import com.umulam.fleen.health.service.external.banking.FlutterwaveService;
import com.umulam.fleen.health.service.external.banking.PaystackService;
import com.umulam.fleen.health.service.impl.FleenHealthEventService;
import com.umulam.fleen.health.service.transaction.TransactionValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static com.umulam.fleen.health.constant.session.TransactionStatus.SUCCESS;
import static com.umulam.fleen.health.service.session.impl.HealthSessionServiceImpl.getMaxMeetingSessionHourDuration;
import static com.umulam.fleen.health.util.StringUtil.getFullName;

@Slf4j
@Service
public class TransactionValidationServiceImpl implements TransactionValidationService {

  private final HealthSessionJpaRepository healthSessionRepository;
  private final SessionTransactionJpaRepository sessionTransactionJpaRepository;
  private final WithdrawalTransactionJpaRepository withdrawalTransactionJpaRepository;
  private final FleenHealthEventService eventService;
  private final ObjectMapper mapper;
  private final BankingService bankingService;
  private final FlutterwaveService flutterwaveService;
  private final PaystackService paystackService;
  private final EarningsService earningsService;

  public TransactionValidationServiceImpl(
                        HealthSessionJpaRepository healthSessionRepository,
                        SessionTransactionJpaRepository sessionTransactionJpaRepository,
                        WithdrawalTransactionJpaRepository withdrawalTransactionJpaRepository,
                        FleenHealthEventService eventService,
                        ObjectMapper mapper,
                        BankingService bankingService,
                        FlutterwaveService flutterwaveService,
                        PaystackService paystackService,
                        EarningsService earningsService) {
    this.healthSessionRepository = healthSessionRepository;
    this.sessionTransactionJpaRepository = sessionTransactionJpaRepository;
    this.withdrawalTransactionJpaRepository = withdrawalTransactionJpaRepository;
    this.eventService = eventService;
    this.mapper = mapper;
    this.bankingService = bankingService;
    this.flutterwaveService = flutterwaveService;
    this.paystackService = paystackService;
    this.earningsService = earningsService;
  }

  @Override
  @Transactional
  public void validateAndCompleteTransaction(String body) {
    PaymentGatewayType paymentGatewayType;
    try {
      PaystackWebhookEvent paystackEvent = mapper.readValue(body, PaystackWebhookEvent.class);
      if (Objects.equals(paystackEvent.getEvent(), PaystackWebhookEventType.CHARGE_SUCCESS.getValue())) {
        paymentGatewayType = PaymentGatewayType.PAYSTACK;
        validateAndCompleteSessionTransaction(bankingService.getInternalPaymentValidationByChargeEvent(body, paymentGatewayType), paymentGatewayType);
      } else if (Objects.equals(paystackEvent.getEvent(), PaystackWebhookEventType.TRANSFER_SUCCESS.getValue()) ||
          Objects.equals(paystackEvent.getEvent(), PaystackWebhookEventType.TRANSFER_FAILED.getValue()) ||
          Objects.equals(paystackEvent.getEvent(), PaystackWebhookEventType.TRANSFER_REVERSED.getValue())) {
        paymentGatewayType = PaymentGatewayType.PAYSTACK;
        validateAndCompleteWithdrawalTransaction(bankingService.getWithdrawalTransferValidationByTransferEvent(body, paymentGatewayType));
      }

      FlutterwaveWebhookEvent flutterwaveEvent = mapper.readValue(body, FlutterwaveWebhookEvent.class);
      if (Objects.equals(flutterwaveEvent.getEvent(), FlutterwaveWebhookEventType.CHARGE_COMPLETED.getValue())) {
        paymentGatewayType = PaymentGatewayType.FLUTTERWAVE;
        validateAndCompleteSessionTransaction(bankingService.getInternalPaymentValidationByChargeEvent(body, paymentGatewayType), paymentGatewayType);
      } else if (Objects.equals(flutterwaveEvent.getEvent(), FlutterwaveWebhookEventType.TRANSFER_COMPLETED.getValue())) {
        paymentGatewayType = PaymentGatewayType.FLUTTERWAVE;
        validateAndCompleteWithdrawalTransaction(bankingService.getWithdrawalTransferValidationByTransferEvent(body, paymentGatewayType));
      }
    } catch (JsonProcessingException ex) {
      log.error(ex.getMessage(), ex);
    }
  }

  private void validateAndCompleteSessionTransaction(InternalPaymentValidation event, PaymentGatewayType paymentGatewayType) {
    List<SessionTransaction> transactions = sessionTransactionJpaRepository.findByGroupReference(event.getTransactionReference());
    List<SessionTransaction> updatedTransactions = new ArrayList<>();

    if (verifyTransactionSuccessStatus(event.getStatus(), event.getTransactionReference(), paymentGatewayType)) {
      if (transactions != null && !transactions.isEmpty()) {
        List<CreateSessionMeetingEvent> meetingEvents = new ArrayList<>();

        for (SessionTransaction transaction : transactions) {
          if (transaction.getStatus() != SUCCESS) {
            transaction.setStatus(SUCCESS);
            transaction.setExternalSystemReference(event.getExternalSystemTransactionReference());
            transaction.setCurrency(event.getCurrency());

            Optional<HealthSession> healthSessionExist = healthSessionRepository.findByReference(transaction.getSessionReference());
            if (healthSessionExist.isPresent()) {
              HealthSession healthSession = healthSessionExist.get();

              if (healthSession.getStatus() != HealthSessionStatus.SCHEDULED && healthSession.getStatus() != HealthSessionStatus.RESCHEDULED) {
                LocalDate meetingDate = healthSession.getDate();
                LocalTime meetingTime = healthSession.getTime();

                LocalDateTime meetingStartDateTime = LocalDateTime.of(meetingDate, meetingTime);
                LocalDateTime meetingEndDateTime = meetingStartDateTime.plusHours(getMaxMeetingSessionHourDuration());

                Member patient = healthSession.getPatient();
                Member professional = healthSession.getProfessional();
                String patientEmail = patient.getEmailAddress();
                String professionalEmail = professional.getEmailAddress();
                String patientName = getFullName(patient.getFirstName(), patient.getLastName());
                String professionalName = getFullName(professional.getFirstName(), professional.getLastName());

                CreateSessionMeetingEvent meetingEvent = CreateSessionMeetingEvent.builder()
                  .startDate(meetingStartDateTime)
                  .endDate(meetingEndDateTime)
                  .attendees(List.of(patientEmail, professionalEmail))
                  .timezone(healthSession.getTimezone())
                  .sessionReference(healthSession.getReference())
                  .patientName(patientName)
                  .professionalName(professionalName)
                  .build();

                CreateSessionMeetingEvent.CreateSessionMeetingEventMetadata eventMetadata = CreateSessionMeetingEvent.CreateSessionMeetingEventMetadata.builder()
                  .sessionReference(healthSession.getReference())
                  .build();
                meetingEvent.setMetadata(getCreateSessionMeetingEventMetadata(eventMetadata));
                meetingEvents.add(meetingEvent);
               }
              updatedTransactions.add(transaction);
            }
          }
        }
        if (!meetingEvents.isEmpty()) {
          eventService.publishCreateSession(CreateSessionMeetingEvents.builder().meetingEvents(meetingEvents).build());
        }
      }
    } else {
      for (SessionTransaction transaction : transactions) {
          transaction.setStatus(TransactionStatus.FAILED);
          transaction.setExternalSystemReference(event.getExternalSystemTransactionReference());
          transaction.setCurrency(event.getCurrency());
          updatedTransactions.add(transaction);
      }
    }

    if (!updatedTransactions.isEmpty()) {
      sessionTransactionJpaRepository.saveAll(updatedTransactions);
    }
  }

  private void validateAndCompleteWithdrawalTransaction(WithdrawalTransferValidation event) {
    Optional<WithdrawalTransaction> transactionExist = withdrawalTransactionJpaRepository.findByReference(event.getReference());
    if (transactionExist.isPresent()) {
      WithdrawalTransaction transaction = transactionExist.get();
      transaction.setExternalSystemReference(event.getExternalTransferReferenceOrCode());
      transaction.setCurrency(event.getCurrency().toUpperCase());
      transaction.setBankName(event.getBankName());
      transaction.setAccountNumber(event.getAccountNumber());
      transaction.setAccountName(event.getFullName());
      transaction.setBankCode(event.getBankCode());
      transaction.setFee(event.getFee());

      if (ExternalTransactionStatus.SUCCESS.getValue().equalsIgnoreCase(event.getStatus()) ||
          ExternalTransactionStatus.SUCCESSFUL.getValue().equalsIgnoreCase(event.getStatus())) {
        if (transaction.getStatus() != SUCCESS) {
          transaction.setStatus(SUCCESS);
          transaction.setWithdrawalStatus(WithdrawalStatus.SUCCESSFUL);
        }
      } else {
        if (ExternalTransactionStatus.FAILED.getValue().equalsIgnoreCase(event.getStatus())) {
          transaction.setStatus(TransactionStatus.FAILED);
          transaction.setWithdrawalStatus(WithdrawalStatus.FAILED);
        } else {
          transaction.setStatus(TransactionStatus.REVERSED);
          transaction.setWithdrawalStatus(WithdrawalStatus.REVERSED);
        }

        if (transaction.getType() == TransactionType.EARNINGS_WITHDRAWAL) {
          earningsService.reverseTransactionAndUpdateEarnings(transaction);
        }
      }
      withdrawalTransactionJpaRepository.save(transaction);
    }
  }

  private Map<String, String> getCreateSessionMeetingEventMetadata(CreateSessionMeetingEvent.CreateSessionMeetingEventMetadata metadata) {
    return mapper.convertValue(metadata, new TypeReference<>() {});
  }

  private boolean verifyTransactionSuccessStatus(String status, String transactionReference, PaymentGatewayType paymentGatewayType) {
    boolean successful = false;
    String actualStatus = "";
    if (paymentGatewayType == PaymentGatewayType.FLUTTERWAVE) {
      actualStatus = flutterwaveService.getTransactionStatusByReference(transactionReference);
    } else if (paymentGatewayType == PaymentGatewayType.PAYSTACK) {
      actualStatus = paystackService.getTransactionStatusByReference(transactionReference);
    }

    if (ExternalTransactionStatus.SUCCESSFUL.getValue().equalsIgnoreCase(status) &&
      ExternalTransactionStatus.SUCCESSFUL.getValue().equalsIgnoreCase(actualStatus)) {
      successful = true;
    } else if (ExternalTransactionStatus.SUCCESS.getValue().equalsIgnoreCase(status) &&
      ExternalTransactionStatus.SUCCESS.getValue().equalsIgnoreCase(actualStatus)) {
      successful = true;
    }
    return successful;
  }
}
