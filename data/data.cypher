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
