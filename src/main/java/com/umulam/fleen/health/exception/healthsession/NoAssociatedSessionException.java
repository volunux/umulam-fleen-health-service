package com.umulam.fleen.health.exception.healthsession;

import com.umulam.fleen.health.exception.base.FleenHealthException;

import java.util.Objects;

public class NoAssociatedSessionException extends FleenHealthException {

  private static final String message = "No associated session exist with this ID. ID: %s";

  public NoAssociatedSessionException(Object id) {
    super(String.format(message, Objects.toString(id, "Unknown")));
  }
}
