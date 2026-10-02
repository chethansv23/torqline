package com.torqline.appointment.constants;

/** Stable error codes returned by the appointment API. */
public final class AppointmentErrorCodes {
    public static final String SLOT_UNAVAILABLE = "SLOT_UNAVAILABLE";
    public static final String OFF_GRID = "OFF_GRID";
    public static final String OUTSIDE_HOURS = "OUTSIDE_HOURS";
    public static final String SLOT_IN_PAST = "SLOT_IN_PAST";
    public static final String SERVICE_NOT_OFFERED = "SERVICE_NOT_OFFERED";
    public static final String NO_BAYS = "NO_BAYS";
    public static final String INVALID_STATE = "INVALID_STATE";

    private AppointmentErrorCodes() {
    }
}
