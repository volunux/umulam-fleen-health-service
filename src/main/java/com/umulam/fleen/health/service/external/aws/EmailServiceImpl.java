package com.umulam.fleen.health.service.external.aws;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umulam.fleen.health.exception.base.FleenHealthException;
import com.umulam.fleen.health.model.dto.mail.EmailDetails;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.umulam.fleen.health.constant.base.ExceptionConstant.FAILED_MAIL_DELIVERY;
import static com.umulam.fleen.health.constant.base.FleenHealthConstant.FAIL_MAIL_DELIVERY;
import static com.umulam.fleen.health.constant.base.FleenHealthConstant.LOGO_FILE_NAME;

@Slf4j
@Component
public class EmailServiceImpl {

  private final SesService sesService;
  private final JavaMailSender mailSender;
  private final Configuration configuration;
  private final ObjectMapper objectMapper;
  private final AmazonSimpleEmailService simpleEmailService;

  public EmailServiceImpl(SesService sesService,
                          JavaMailSender mailSender,
                          Configuration configuration,
                          ObjectMapper objectMapper,
                          AmazonSimpleEmailService simpleEmailService) {
    this.sesService = sesService;
    this.mailSender = mailSender;
    this.configuration = configuration;
    this.objectMapper = objectMapper;
    this.simpleEmailService = simpleEmailService;
  }

  private SimpleMailMessage createMailMessage(EmailDetails details) {
    SimpleMailMessage mailMessage = new SimpleMailMessage();
    mailMessage.setFrom(details.getFrom());
    mailMessage.setTo(details.getTo());
    mailMessage.setSubject(details.getSubject());
    mailMessage.setText(details.getPlainText());
    return mailMessage;
  }

  public boolean sendMessage(EmailDetails emailDetails) {
    sesService.sendMessage(createMailMessage(emailDetails));
    return true;
  }

  public void sendHtmlMessage(EmailDetails details) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = initMimeMessageHelper(message, details);
      if (Objects.isNull(helper)) {
        throw new FleenHealthException(FAILED_MAIL_DELIVERY);
      }

      InputStreamSource data =
              new ByteArrayResource("".getBytes());
      helper.addAttachment(LOGO_FILE_NAME, data);
      mailSender.send(message);
    } catch (MessagingException | MailSendException ex) {
      log.error(ex.getMessage(), ex);
      throw new FleenHealthException(FAILED_MAIL_DELIVERY);
    }
  }

  public boolean sendMessageWithAttachment(EmailDetails details, List<File> files) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = initMimeMessageHelper(message, details);
      if (Objects.isNull(helper)) {
        throw new FleenHealthException(FAILED_MAIL_DELIVERY);
      }

      for (File file : files) {
        helper.addAttachment(file.getName(), file);
      }

      helper.setFrom(details.getFrom());
      helper.setTo(details.getTo());
      helper.setSubject(details.getSubject());

      setEmailBody(helper, details);
      mailSender.send(message);

    } catch (MessagingException ex) {
     log.error(ex.getMessage(), ex);
      throw new FleenHealthException(FAILED_MAIL_DELIVERY);
    }
    return true;
  }

  public boolean sendTemplatedMessage(SimpleMailMessage mailMessage, String templateName, Map<String, Object> data) {
    Destination destination = new Destination();
    List<String> toAddresses = new ArrayList<>();
    String[] emails = mailMessage.getTo();
    Collections.addAll(toAddresses, Objects.requireNonNull(emails));
    destination.setToAddresses(toAddresses);

    try {
      SendTemplatedEmailRequest templatedEmailRequest = new SendTemplatedEmailRequest();
      templatedEmailRequest.withSource(mailMessage.getFrom());
      templatedEmailRequest.withDestination(destination);
      templatedEmailRequest.withTemplate(templateName);
      templatedEmailRequest.withTemplateData(objectMapper.writeValueAsString(data));
      simpleEmailService.sendTemplatedEmail(templatedEmailRequest);
    } catch (JsonProcessingException ex) {
      log.error(ex.getMessage(), ex);
    }
    return true;
  }

  public String processAndReturnTemplate(String templateName, Map<String, Object> data) {
    try {
      Template template1 = new Template("", "", null);
      Template template = configuration.getTemplate(templateName);
      return FreeMarkerTemplateUtils.processTemplateIntoString(template, data);
    } catch (TemplateException | IOException ex) {
      log.error(ex.getMessage(), ex);
    }
    return null;
  }

  public boolean sendMessage(String from, String to, String subject, String body) {
    try {
      SendEmailRequest request = new SendEmailRequest();
      request.withSource(from);
      request.withDestination(new Destination().withToAddresses(to));
      Message message = new Message();
      message.withBody(new Body()
              .withHtml(new Content()
                      .withData(body)
                      .withCharset(StandardCharsets.UTF_8.name())));
      message.withSubject(new Content()
              .withData(subject)
              .withCharset(StandardCharsets.UTF_8.name()));
      request.withMessage(message);
      simpleEmailService.sendEmail(request);
    } catch (RuntimeException ex) {
      log.error(ex.getMessage(), ex);
    }
    return false;
  }

  private MimeMessageHelper initMimeMessageHelper(MimeMessage message, EmailDetails details) {
    try {
      MimeMessageHelper helper = new MimeMessageHelper(
              message,
              MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
              StandardCharsets.UTF_8.name());

      helper.setFrom(details.getFrom());
      helper.setTo(details.getTo());
      helper.setSubject(details.getSubject());
      setEmailBody(helper, details);
      return helper;
    } catch (MessagingException ex) {
      log.error(ex.getMessage(), ex);
    }
    return null;
  }

  private void setEmailBody(MimeMessageHelper helper, EmailDetails details) {
    try {
      if (Objects.nonNull(details.getPlainText())
          && Objects.nonNull(details.getHtmlText())) {
        helper.setText(details.getPlainText(), details.getHtmlText());
      } else if (Objects.nonNull(details.getHtmlText())) {
        helper.setText(details.getHtmlText(), true);
      } else if (Objects.nonNull(details.getPlainText())) {
        helper.setText(details.getPlainText());
      }
      return;
    } catch (MessagingException ex) {
      log.error(ex.getMessage(), ex);
    }
    throw new MailSendException(FAIL_MAIL_DELIVERY);
  }
}
