package com.umulam.fleen.health.model.dto.professional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.umulam.fleen.health.validator.IsNumber;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfessionalDetailsDto {

  @NotBlank(message = "{professional.title.notEmpty}")
  @Size(min = 1, max = 500, message = "{professional.title.size}")
  private String title;

  @NotNull(message = "{professional.yearsOfExperience.notNull}")
  @IsNumber(message = "{professional.yearsOfExperience.isNumber}")
  @Size(min = 1, max = 2, message = "{professional.yearsOfExperience.size}")
  @JsonProperty("years_of_experience")
  private String yearsOfExperience;

  @Size(max = 2500, message = "{professional.areaOfExpertise.size}")
  @JsonProperty("area_of_expertise")
  private String areaOfExpertise;
}