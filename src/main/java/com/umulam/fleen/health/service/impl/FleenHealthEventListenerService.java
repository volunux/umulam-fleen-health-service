package com.umulam.fleen.health.service.impl;

import com.google.api.services.calendar.model.Event;
import com.umulam.fleen.health.constant.session.HealthSessionStatus;
import com.umulam.fleen.health.event.CancelSessionMeetingEvent;
import com.umulam.fleen.health.event.CreateSessionMeetingEvent;
import com.umulam.fleen.health.model.domain.HealthSession;
import com.umulam.fleen.health.repository.jpa.HealthSessionJpaRepository;
import com.umulam.fleen.health.service.external.google.CalendarService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Service
public class FleenHealthEventListenerService {

  private final CalendarService calendarService;
  private final HealthSessionJpaRepository healthSessionRepository;

  public FleenHealthEventListenerService(CalendarService calendarService,
                                         HealthSessionJpaRepository healthSessionRepository) {
    this.calendarService = calendarService;
    this.healthSessionRepository = healthSessionRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void createMeetingSession(CreateSessionMeetingEvent meetingEvent) {
    Event event = calendarService.createEvent(meetingEvent.getStartDate(), meetingEvent.getEndDate(), meetingEvent.getAttendees(), meetingEvent.getMetadata());
    Optional<HealthSession> healthSessionExist = healthSessionRepository.findByReference(meetingEvent.getSessionReference());
    if (healthSessionExist.isPresent()) {
      HealthSession healthSession = healthSessionExist.get();
      healthSession.setEventReferenceOrId(event.getId());
      healthSession.setOtherEventReference(event.getICalUID());
      healthSession.setMeetingUrl(event.getHangoutLink());
      healthSession.setEventLink(event.getHtmlLink());
      healthSession.setStatus(HealthSessionStatus.APPROVED);
      healthSessionRepository.save(healthSession);
    }
  }

  @TransactionalEventListener
  public void cancelMeetingSession(CancelSessionMeetingEvent meetingEvent) {
    calendarService.cancelEvent(meetingEvent.getEventIdOrReference());
  }
}
