package com.umulam.fleen.health.adapter.paystack;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Builder
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "paystack")
public class PaystackConfig {

  @NotBlank
  private String secretKey;

  @NotBlank
  private String publicKey;

  @NotEmpty
  private List<String> ipWhitelist;
}
