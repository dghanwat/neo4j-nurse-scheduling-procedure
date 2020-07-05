package com.dghanwat.neo4j.nurse.scheduling.schema;

public final class Properties {
    private Properties() {
        throw new IllegalAccessError("Utility class");
    }

    public static final String NAME = "name";
    public static final String START_TIME = "startTime";
    public static final String END_TIME = "endTime";
    public static final String DAY_OF_WEEK = "dayOfWeek";
    public static final String MIN_NURSES = "minNurses";

}
