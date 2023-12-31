package com.umulam.fleen.health.adapter.banking.flutterwave.model.enums;

import com.umulam.fleen.health.adapter.EndpointBlock;

public enum FlutterwaveEndpointBlock implements EndpointBlock {

  BANKS("/banks"),
  ACCOUNTS("/accounts"),
  RESOLVE("/resolve"),
  VERIFY("/verify"),
  TRANSACTIONS("/transactions"),
  VERIFY_BY_REFERENCE("/verify_by_reference"),
  REFUND("/refund"),
  TRANSFERS("/transfers"),
  RATES("/rates"),
  RETRIES("/retries"),
  BRANCHES("/branches"),
  FEE("/fee");

  private final String value;

  FlutterwaveEndpointBlock(String value) {
    this.value = value;
  }

  @Override
  public String getValue() {
    return value;
  }
}
