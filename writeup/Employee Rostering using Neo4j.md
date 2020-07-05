# Employee Rostering using Neo4j

Employee rostering can be a tough task even on the best of days. Let's see if Neo4j can help us and make this problem more manageable to tackle

## Overview

This is the first part of two part article in which I propose a solution to famous Nurse scheduling problem using Neo4j. In this post we will solve the rostering problem and in the following post we we will write an optimization function to find the best solution for many possible solutions.

Though the post talks about Nurse scheduling it can be extended to any employee rostering problem.

## Introduction

Nurse Rostering Problem (NRP) or Nurse Scheduling Problem (NSP), one of the types of staff scheduling, arose from the need to develop a decision-making tool for the appointment of nurses in line with nurse preferences and patient workload requirements. The NSP represents a subclass of Constraint Satisfaction Problems (CSP), which includes a number of restraints. Finding a schedule that can satisfy all the constraints can be computationally very difficult. 

In this post we will create nurse schedule for 9 nurses, over a period of 3 days with following hard constraints

- Each day is divided into three 8-hour shifts.
- Each shift has a minimum requirement of number of nurses required
- Nurse can work in only one shift in a day
- Nurse needs minimum 8 hours of resting before she can be assigned to another shift
- Each shift should take into consideration Nurse holidays 

## Data Model

Let's start with how our nurse data model looks like.

By executing the cypher script below we load our data model

``` cypher
MERGE (n1: Nurse{name:"Anne"})
MERGE (n2: Nurse{name:"Bethanie"})
MERGE (n3: Nurse{name:"Besty"})
MERGE (n4: Nurse{name:"Cathy"})
MERGE (n5: Nurse{name:"Debbie"})
MERGE (n6: Nurse{name:"Kirsty"})
MERGE (n7: Nurse{name:"Emma"})
MERGE (n8: Nurse{name:"Billy"})
MERGE (n9: Nurse{name:"Susan"})

MERGE (d1: Day{dayOfWeek: "Monday"})
MERGE (d2: Day{dayOfWeek: "Tuesday"})
MERGE (d3: Day{dayOfWeek: "Wednesday"})

MERGE (h1: Holiday{dayOfWeek: "Monday"})
MERGE (h2: Holiday{dayOfWeek: "Tuesday"})
MERGE (h3: Holiday{dayOfWeek: "Wednesday"})

CREATE (sh1: Shift{startTime:"00:00",endTime:"07:59" , minNurses:2})
CREATE (sh2: Shift{startTime:"08:00",endTime:"15:59", minNurses:1})
CREATE (sh3: Shift{startTime:"16:00",endTime:"23:59", minNurses:3})

CREATE (sh4: Shift{startTime:"00:00",endTime:"07:59", minNurses:2})
CREATE (sh5: Shift{startTime:"08:00",endTime:"15:59", minNurses:2})
CREATE (sh6: Shift{startTime:"16:00",endTime:"23:59", minNurses:2})

CREATE (sh7: Shift{startTime:"00:00",endTime:"07:59", minNurses:1})
CREATE (sh8: Shift{startTime:"08:00",endTime:"15:59", minNurses:3})
CREATE (sh9: Shift{startTime:"16:00",endTime:"23:59", minNurses:2})

MERGE(d1)-[:HAS_SHIFT]->(sh1)
MERGE(d1)-[:HAS_SHIFT]->(sh2)
MERGE(d1)-[:HAS_SHIFT]->(sh3)

MERGE(d2)-[:HAS_SHIFT]->(sh4)
MERGE(d2)-[:HAS_SHIFT]->(sh5)
MERGE(d2)-[:HAS_SHIFT]->(sh6)

MERGE(d3)-[:HAS_SHIFT]->(sh7)
MERGE(d3)-[:HAS_SHIFT]->(sh8)
MERGE(d3)-[:HAS_SHIFT]->(sh9)

MERGE(d1)-[:NEXT_DAY]->(d2)
MERGE (d2)-[:NEXT_DAY]->(d3)

MERGE (sh1)-[:NEXT_SHIFT]->(sh2)
MERGE (sh2)-[:NEXT_SHIFT]->(sh3)
MERGE (sh3)-[:NEXT_SHIFT]->(sh4)
MERGE (sh4)-[:NEXT_SHIFT]->(sh5)
MERGE (sh5)-[:NEXT_SHIFT]->(sh6)
MERGE (sh6)-[:NEXT_SHIFT]->(sh7)
MERGE (sh7)-[:NEXT_SHIFT]->(sh8)
MERGE (sh8)-[:NEXT_SHIFT]->(sh9)

MERGE (n1)-[:ON_VACATION]->(h1)
MERGE (n2)-[:ON_VACATION]->(h3)
MERGE (n6)-[:ON_VACATION]->(h2)
MERGE (n7)-[:ON_VACATION]->(h1)

```



![image-20200705155526177](C:\Users\A506826\AppData\Roaming\Typora\typora-user-images\image-20200705155526177.png)

We have defined the minimum number of nurses required for the shift as a node property of the shift

```CREATE (sh1: Shift{startTime:"00:00",endTime:"07:59" , minNurses:2})```

## Solution Approach

1. *Traverse all the shifts as a path starting from first shift to last shift*
2. *For every shift compute potential resources which satisfy all the constraints.*
3. *Rank every potential resource based ranking criteria*
4. *Select the top ranked resource(s) assign it to shift based on the minimum number of resources constraint*
5. *Move to next shift in the path* 
6. *Repeat from Step 2*

As we are storing every thing a directed graph its very easy to traverse back and forth to validate for constraint satisfaction

## Show me the code

We start by writing a stored procedure in Neo4j. If you are new to writing Neo4j stored procedure, please refer this link  https://neo4j.com/docs/java-reference/current/extending-neo4j/procedures-and-functions/procedures/

```java
@org.neo4j.procedure.Procedure(name = "com.dghanwat.neo4j.nurse.scheduling.solve", mode = Mode.WRITE)
@Description("CALL com.dghanwat.neo4j.nurse.scheduling.solve()")
public Stream<Response> solve() {
    List<Node> resources = findAllNurses();
    Path allShifts = findShifts();
    for (Node shift : allShifts.nodes()) {
        List<Node> possibleResources = findPossibleResources(shift, resources);
        LinkedHashMap<Node, Integer> sorted = rankPossibleResources(shift, possibleResources, resourcesMappedByScore);
        int minimumResourcesRequiredForShift = Utils.getInteger(shift, Properties.MIN_NURSES);
                
        for (Node resourceToBeAssigned : sorted.keySet()) {
            Relationship newRelationshipCreated = resourceToBeAssigned.createRelationshipTo(shift, RelationshipTypes.IS_ASSIGNED_TO);
            long resourcesAssignedCount = StreamSupport.stream(shift                                                               .getRelationships(RelationshipTypes.IS_ASSIGNED_TO, Direction.INCOMING)
                                                               .spliterator(), false)
                .count();
            if (resourcesAssignedCount >= minimumResourcesRequiredForShift) {
                break;
            }
        }
    }
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
```

Code to check constraint **Nurse can work in only one shift in a day**

```java
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
```

Code to check constraint **Nurse needs minimum 8 hours of resting before she can be assigned to another shift**

```java
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
```

Code to check constraint **Each shift should take into consideration Nurse holidays **

```java
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
```

## Solution

Execute the stored procedure

```cypher
CALL com.dghanwat.neo4j.nurse.scheduling.solve() YIELD response RETURN response
```

This should give a schedule which matches all the above hard constraints

![image-20200705162323392](C:\Users\A506826\AppData\Roaming\Typora\typora-user-images\image-20200705162323392.png)

```cypher
MATCH (d:Day)-[:HAS_SHIFT]->(s:Shift)<-[:IS_ASSIGNED_TO]-(n:Nurse)
RETURN d.dayOfWeek, s.startTime,s.endTime,s.minNurses, COLLECT(n.name) 
ORDER BY d.dayOfWeek,s.startTime
```

The above cypher gives you result in tabular format

![image-20200705162834636](C:\Users\A506826\AppData\Roaming\Typora\typora-user-images\image-20200705162834636.png)

Pretty neat, right? 

## Conclusion

There are many solutions to rostering problem using tools like Google OR tools or OptaPlanner. In the above post I have tried to solve this problem using Neo4j. It many not scale as other constraint satisfaction technologies, but the main purpose of using graph to model and solve it is we can then apply other Graph algorithms like Centrality or Community detection to find hidden patterns / relationships between resources. It might also give us insights into finding alternative plans based on the existing relationships.

As usual you can find the code in my Github repository here https://github.com/dghanwat/neo4j-nurse-scheduling-procedure

*Disclaimer: This is just an attempt to solve a complex constraint satisfaction problem using a different technology. By no means this post claims that it is the best solution*