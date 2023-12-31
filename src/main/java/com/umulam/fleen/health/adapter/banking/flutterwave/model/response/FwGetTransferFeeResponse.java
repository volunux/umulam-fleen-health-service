package com.umulam.fleen.health.adapter.banking.flutterwave.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FwGetTransferFeeResponse extends FlutterwaveResponse {

  private List<TransferFeeData> data;

  @Getter
  @Setter
  @NoArgsConstructor
  public static class TransferFeeData {

    private String currency;

    @JsonProperty("fee_type")
    private String feeType;

    private Double fee;
  }
}
