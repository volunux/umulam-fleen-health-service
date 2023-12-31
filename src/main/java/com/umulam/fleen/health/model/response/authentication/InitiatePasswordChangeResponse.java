package com.umulam.fleen.health.model.response.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePasswordChangeResponse {

  @JsonProperty("access_token")
  public String accessToken;
}
