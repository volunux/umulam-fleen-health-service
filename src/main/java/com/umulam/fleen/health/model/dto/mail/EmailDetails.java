package com.umulam.fleen.health.model.dto.mail;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailDetails {

  private String from;
  private String to;
  private String subject;
  private String htmlText;
  private String plainText;
}
