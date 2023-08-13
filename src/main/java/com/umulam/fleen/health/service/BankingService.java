package com.umulam.fleen.health.service;

import com.umulam.fleen.health.constant.authentication.PaymentGatewayType;
import com.umulam.fleen.health.model.dto.banking.CreateWithdrawalDto;
import com.umulam.fleen.health.model.event.InternalPaymentValidation;
import com.umulam.fleen.health.model.response.SupportedCountry;
import com.umulam.fleen.health.model.security.FleenUser;

import java.util.List;

public interface BankingService {

  String getTransactionStatusByReference(String transactionReference);

  InternalPaymentValidation getInternalPaymentValidationByChargeEvent(String body, PaymentGatewayType paymentGatewayType);

  List<SupportedCountry> getSupportedCountries();

  boolean isBankCodeExists(String bankCode, String countryOrCurrency);

  void createWithdrawal(CreateWithdrawalDto dto, FleenUser user);
}