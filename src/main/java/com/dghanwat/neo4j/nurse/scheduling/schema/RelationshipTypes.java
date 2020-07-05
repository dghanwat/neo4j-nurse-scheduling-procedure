package com.dghanwat.neo4j.nurse.scheduling.schema;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.RelationshipType;

public enum RelationshipTypes implements RelationshipType {
    HAS_SHIFT,
    NEXT_DAY,
    NEXT_SHIFT,
    IS_ASSIGNED_TO,
    ON_VACATION;
}
