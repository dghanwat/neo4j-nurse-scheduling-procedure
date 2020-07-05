package com.dghanwat.neo4j.nurse.scheduling.procedure;

import com.dghanwat.neo4j.nurse.scheduling.response.Response;
import com.dghanwat.neo4j.nurse.scheduling.schema.Labels;
import com.dghanwat.neo4j.nurse.scheduling.schema.Properties;
import com.dghanwat.neo4j.nurse.scheduling.schema.RelationshipTypes;
import com.dghanwat.neo4j.nurse.scheduling.utils.Utils;
import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class Solver {

    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseService db;

    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `logs/neo4j.log`
    @Context
    public Log log;

    @org.neo4j.procedure.Procedure(name = "com.dghanwat.neo4j.nurse.scheduling.solve", mode = Mode.WRITE)
    @Description("CALL com.dghanwat.neo4j.nurse.scheduling.solve()")
    public Stream<Response> solve() {
        List<Response> responseList = new ArrayList<>();
        Response response = new Response();
        try {
            List<Node> resources = findAllNurses();
            Path allShifts = findShifts();
            for (Node shift : allShifts.nodes()) {
                Node day = shift.getRelationships(RelationshipTypes.HAS_SHIFT, Direction.INCOMING).iterator().next().getStartNode();
                log.debug("Shift is %s , Start time %s , End Time %s, Day of Week %s",
                        shift.getId(),
                        Utils.getString(shift, Properties.START_TIME),
                        Utils.getString(shift, Properties.END_TIME),
                        Utils.getString(day, Properties.DAY_OF_WEEK));
                List<Node> possibleResources = findPossibleResources(shift, resources);
                Map<Node,Integer> resourcesMappedByScore = new HashMap<>();
                LinkedHashMap<Node, Integer> sorted = rankPossibleResources(shift, possibleResources, resourcesMappedByScore);

                int minimumResourcesRequiredForShift = Utils.getInteger(shift, Properties.MIN_NURSES);
                log.debug("Number of available resources for shift %s are %s",shift.getId() , sorted.values().size());
                log.debug("Minimum Number of nurses required for Shift %s are %s", shift.getId(), minimumResourcesRequiredForShift);
                for (Node resourceToBeAssigned : sorted.keySet()) {
                    Relationship newRelationshipCreated = resourceToBeAssigned.createRelationshipTo(shift, RelationshipTypes.IS_ASSIGNED_TO);
                    log.debug("Assigned resource %s to shift %s",
                            Utils.getString(newRelationshipCreated.getStartNode(), Properties.NAME)
                            , Utils.getString(newRelationshipCreated.getEndNode(), Properties.START_TIME));
                    long resourcesAssignedCount = StreamSupport.stream(shift
                            .getRelationships(RelationshipTypes.IS_ASSIGNED_TO, Direction.INCOMING)
                            .spliterator(), false)
                            .count();
                    log.debug("Number of Nurses assigned so far shift %s are %s", shift.getId(), resourcesAssignedCount);
                    if (resourcesAssignedCount >= minimumResourcesRequiredForShift) {
                        log.debug("Assigned minimum required nurses to shift %s", shift.getId());
                        break;
                    }

                }


            }
            responseList.add(response);
        } catch (Exception ex) {
            log.error("Some error in solving the problem", ex);
        }

        return responseList.stream();

    }

    private LinkedHashMap<Node, Integer> rankPossibleResources(Node shift, List<Node> possibleResources, Map<Node, Integer> resourcesMappedByScore) {
        for (Node r : possibleResources) {
            int countOfShifts = findCountOfShiftsAssignedToResource(shift, r, 0);
            log.debug("Nurse Name %s", Utils.getString(r, Properties.NAME));
            resourcesMappedByScore.put(r,new Integer(countOfShifts));
        }

        //Sort it resources map so that we have the one with highest score at top and
        // it becomes the most eligible candidate to be assigned.
        return resourcesMappedByScore
                .entrySet()
                .stream()
                .sorted(comparingByValue())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
    }

    private List<Node> findAllNurses() {
        return db.findNodes(Labels.Nurse).stream().collect(Collectors.toList());
    }

    private Path findShifts() {
        String s = "MATCH p = (s1:Shift)-[:NEXT_SHIFT*]->(s2:Shift) RETURN p";
        Result result = db.execute(s);
        Path path = null;
        while (result.hasNext()) {
            path = (Path) result.next().get("p");
        }
        return path;
    }

    private List<Node> findPossibleResources(Node shift, List<Node> allResources) {
        List<Node> possibleResources = new ArrayList<>();
        for (Node resource : allResources) {
            if (!isResourceAlreadyAssigned(shift, resource)
                    && canResourceBeAssigned(shift, resource)) {
                if (resourceIsNotOnHoliday(shift, resource)) {
                    possibleResources.add(resource);
                }
            }
        }

        return possibleResources;
    }

    private boolean resourceIsNotOnHoliday(Node shift, Node resource) {
        if (resource.hasRelationship(RelationshipTypes.ON_VACATION, Direction.OUTGOING)) {
            String shiftDay = Utils.getString(shift
                    .getSingleRelationship(RelationshipTypes.HAS_SHIFT, Direction.INCOMING)
                    .getStartNode(), Properties.DAY_OF_WEEK);
            Iterator<Relationship> holidays = resource.getRelationships(RelationshipTypes.ON_VACATION, Direction.OUTGOING).iterator();
            while (holidays.hasNext()) {
                String resourceHoliday = Utils.getString(holidays.next().getEndNode(), Properties.DAY_OF_WEEK);
                if (resourceHoliday.equalsIgnoreCase(shiftDay)) {
                    // resource is on holiday , so make it un available
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isResourceAlreadyAssigned(Node shift, Node resource) {
        if (shift.hasRelationship(RelationshipTypes.IS_ASSIGNED_TO)) {
            Iterator<Relationship> relationships = shift
                    .getRelationships(RelationshipTypes.IS_ASSIGNED_TO, Direction.INCOMING)
                    .iterator();
            while (relationships.hasNext()) {
                if (relationships.next().getStartNode().getId() == resource.getId()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canResourceBeAssigned(Node shift, Node resource) {
        if (isNurseWorkingForMoreThanOneShiftOnADay(shift, resource)) {
            return false;
        } else {
            return !isNurseWorkingForPreviousShift(shift, resource);
        }
    }

    private boolean isNurseWorkingForMoreThanOneShiftOnADay(Node shift, Node resource) {
        String cypher = "MATCH (s:Shift)<-[:HAS_SHIFT]-(d:Day) " +
                " WHERE id(s)=" + shift.getId() +
                " WITH d as shiftDay " +
                " MATCH (shiftDay)-[:HAS_SHIFT]->(s1:Shift) " +
                " WITH s1 as shift " +
                " MATCH (shift)<-[:IS_ASSIGNED_TO]-(n:Nurse) " +
                " WHERE id(n) = " + resource.getId() +
                " RETURN n.name";
        Result result = db.execute(cypher);
        return result.hasNext();
    }

    private boolean isNurseWorkingForPreviousShift(Node shift, Node resource) {
        if (shift.hasRelationship(RelationshipTypes.NEXT_SHIFT, Direction.INCOMING)) {
            Node previousShift = shift.getSingleRelationship(RelationshipTypes.NEXT_SHIFT, Direction.INCOMING)
                    .getStartNode();
            if (isResourceAlreadyAssigned(previousShift, resource)) {
                return true;
            } else {
                return false;
            }
        } else {
            // This is the first shift
            return false;
        }
    }

    private int findCountOfShiftsAssignedToResource(Node shift, Node resource, int count) {
        if (shift.hasRelationship(RelationshipTypes.NEXT_SHIFT, Direction.INCOMING)) {
            Node previousShift = shift.getSingleRelationship(RelationshipTypes.NEXT_SHIFT, Direction.INCOMING)
                    .getStartNode();
            if (isResourceAlreadyAssigned(previousShift, resource)) {
                count++;
            }
            return findCountOfShiftsAssignedToResource(previousShift, resource, count);
        } else {
            return count;
        }

    }
}
