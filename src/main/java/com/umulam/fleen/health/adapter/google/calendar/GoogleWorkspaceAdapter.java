package com.umulam.fleen.health.adapter.google.calendar;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.DirectoryScopes;
import com.google.api.services.admin.directory.model.Alias;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;

import static com.google.api.services.admin.directory.DirectoryScopes.ADMIN_DIRECTORY_USER;
import static com.google.api.services.admin.directory.DirectoryScopes.ADMIN_DIRECTORY_USER_ALIAS;
import static com.google.api.services.calendar.CalendarScopes.CALENDAR;
import static com.google.api.services.calendar.CalendarScopes.CALENDAR_EVENTS;
import static com.google.api.services.gmail.GmailScopes.GMAIL_SETTINGS_BASIC;
import static com.google.api.services.gmail.GmailScopes.GMAIL_SETTINGS_SHARING;

//@Configuration
public class GoogleWorkspaceAdapter {

  private static final String APPLICATION_NAME = "Lam Health";
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final List<String> SCOPES = List.of(
          CALENDAR, CALENDAR_EVENTS,
          ADMIN_DIRECTORY_USER, ADMIN_DIRECTORY_USER_ALIAS,
          GMAIL_SETTINGS_BASIC, GMAIL_SETTINGS_SHARING);
  private static final String SERVICE_CREDENTIALS_FILE_PATH = "/secret/service-credential.json";

//  @Bean
  public Calendar getCalendar() throws GeneralSecurityException, IOException {
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(getServiceCredential()))
            .setApplicationName(APPLICATION_NAME)
            .build();
  }

//  @Bean
  public Directory getDirectory() throws IOException, GeneralSecurityException {
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    return new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(getServiceCredential()))
            .setApplicationName(APPLICATION_NAME)
            .build();
  }

//  @Bean
  public Gmail getGmail() throws IOException, GeneralSecurityException {
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(getServiceCredential()))
            .setApplicationName(APPLICATION_NAME)
            .build();
  }


  private static InputStream getCredentialInputStream() throws IOException {
    InputStream in = GoogleWorkspaceAdapter.class.getResourceAsStream(GoogleWorkspaceAdapter.SERVICE_CREDENTIALS_FILE_PATH);
    if (in == null) {
      throw new FileNotFoundException("Resource not found: ".concat(GoogleWorkspaceAdapter.SERVICE_CREDENTIALS_FILE_PATH));
    }
    return in;
  }

  public GoogleCredentials getServiceCredential() throws IOException {
    GoogleCredentials credentials = GoogleCredentials.fromStream(
            Objects.requireNonNull(getCredentialInputStream()))
            .createScoped(SCOPES)
            .createDelegated("umulam@volunux.com");
    credentials.refreshIfExpired();
    System.out.println(credentials);
    return credentials;
  }
}
