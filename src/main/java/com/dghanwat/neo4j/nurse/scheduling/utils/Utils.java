package com.dghanwat.neo4j.nurse.scheduling.utils;

import com.dghanwat.neo4j.nurse.scheduling.schema.Properties;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;

public class Utils {

    /**
     * Utility method to return property value of node
     *
     * @param node     - Node in graph
     * @param property - Property for which value is to be returned
     * @return - Property value in required type
     */
    public static String getString(Node node, String property) {
        if (node != null) {
            return ((String) node.getProperty(property));
        } else {
            return "";
        }
    }

    public static Integer getInteger(Node node, String property) {
        if (node != null) {
            return ((Long) node.getProperty(property)).intValue();
        } else {
            return 0;
        }
    }

    /**
     * returns the current traversal path in String format
     *
     * @param path - Traversal Path
     * @return - Path is String format (to debug)
     */
    public static String getPath(Path path) {
        StringBuilder pathToPrint = new StringBuilder();
        Node n = path.startNode();
        pathToPrint.append("-- (").append(n.getId()).append(":").append(n.getLabels()).append(":").append(Utils.getString(n, Properties.START_TIME)).append(":").append(Utils.getString(n, Properties.END_TIME)).append(")");
        for (Relationship r : path.relationships()) {
            String start = "";
            String end = "";
            if (r.getStartNode().equals(n)) {
                end = ">";
            } else {
                start = "<";
            }
            pathToPrint.append(start).append("-[;").append(r.getType()).append("]-").append(end);
            n = r.getOtherNode(n);
            pathToPrint.append("(").append(n.getId()).append(":").append(Utils.getString(n, Properties.START_TIME)).append(":").append(Utils.getString(n, Properties.END_TIME)).append(")");
        }

        return pathToPrint.toString();
    }
}
