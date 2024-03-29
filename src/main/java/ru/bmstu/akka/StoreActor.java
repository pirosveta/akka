package ru.bmstu.akka;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;

import java.util.ArrayList;
import java.util.HashMap;

public class StoreActor extends AbstractActor {
    private final int PACKAGE_ID_COLUMN = 0, TEST_NAME_COLUMN = 1, VALUE_COLUMN = 2,
            ESTIMATED_VALUE = 0, CALCULATED_VALUE = 1, EQUAL_VALUES = 0;

    private static HashMap<String, HashMap<String, ArrayList<String>>> total = new HashMap<>();

    @Override
    public Receive createReceive() throws NullPointerException {
        return ReceiveBuilder.create().match(Pair.class, input -> {
            Pair<PackageDefinition, TestsDefinition> pair = (Pair<PackageDefinition, TestsDefinition>) input;
            PackageDefinition pack = pair.first();
            TestsDefinition test = pair.second();
            if (!total.containsKey(pack.getPackageId())) {
                total.put(pack.getPackageId(), new HashMap<>());
            }
            HashMap<String, ArrayList<String>> results = total.get(pack.getPackageId());
            if (!results.containsKey(test.getTestName())) {
                results.put(test.getTestName(), new ArrayList<>());
            }
            ArrayList<String> values = results.get(test.getTestName());
            values.add(test.getExpectedResult());
            results.replace(test.getTestName(), values);
            total.replace(pack.getPackageId(), results);
        })
        .match(String[].class, input -> {
            HashMap<String, ArrayList<String>> results = total.get(input[PACKAGE_ID_COLUMN]);
            ArrayList<String> values = results.get(input[TEST_NAME_COLUMN]);
            values.add(input[VALUE_COLUMN]);
            results.replace(input[TEST_NAME_COLUMN], values);
            total.replace(input[PACKAGE_ID_COLUMN], results);
        })
        .match(String.class, packageId -> {
            HashMap<String, Boolean> output = new HashMap<>();
            HashMap<String, ArrayList<String>> results = total.get(packageId);
            for (String testName : results.keySet()) {
                ArrayList<String> values = results.get(testName);
                output.put(testName,
                        values.get(ESTIMATED_VALUE).compareTo(values.get(CALCULATED_VALUE)) == EQUAL_VALUES);
            }
            getSender().tell(output, ActorRef.noSender());
        })
        .build();
    }
}
