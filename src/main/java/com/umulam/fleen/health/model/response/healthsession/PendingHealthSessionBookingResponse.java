package com.umulam.fleen.health.model.response.healthsession;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

import static com.umulam.fleen.health.util.DateFormatUtil.DATE_TIME;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingHealthSessionBookingResponse {

  @JsonProperty("session_reference")
  private String sessionReference;

  @JsonProperty("member_first_name")
  private String patientFirstName;

  @JsonProperty("member_last_name")
  private String patientLastName;

  @JsonProperty("member_email_address")
  private String patientEmailAddress;

  private String timezone;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_TIME)
  @JsonProperty("start_date")
  private LocalDateTime startDate;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_TIME)
  @JsonProperty("end_date")
  private LocalDateTime endDate;
}