package com.umulam.fleen.health.service.impl;

import com.umulam.fleen.health.adapter.banking.flutterwave.model.response.FwGetExchangeRateResponse;
import com.umulam.fleen.health.service.ExchangeRateService;
import com.umulam.fleen.health.service.external.banking.FlutterwaveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ExchangeRateServiceImpl implements ExchangeRateService {

  private final ConfigService configService;
  private final FlutterwaveService flutterwaveService;

  public ExchangeRateServiceImpl(ConfigService configService,
                                 FlutterwaveService flutterwaveService) {
    this.configService = configService;
    this.flutterwaveService = flutterwaveService;
  }

  @Override
  public Double getConvertedHealthSessionPrice(Double amount) {
    FwGetExchangeRateResponse exchangeRate = flutterwaveService.getExchangeRate(amount, configService.getHealthSessionPaymentCurrency(), configService.getHealthSessionPricingCurrency());
    return exchangeRate.getData().getSource().getAmount();
  }
}
