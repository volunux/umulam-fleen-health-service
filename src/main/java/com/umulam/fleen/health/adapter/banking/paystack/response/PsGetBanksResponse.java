package com.umulam.fleen.health.adapter.banking.paystack.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PsGetBanksResponse extends PaystackResponse {

  private List<PsBankData> data;

  @Getter
  @Setter
  @NoArgsConstructor
  public static class PsBankData {
    private String name;
    private String code;
    private String currency;
  }
}
