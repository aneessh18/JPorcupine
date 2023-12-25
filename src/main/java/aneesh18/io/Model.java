package aneesh18.io;


import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public interface Model {

    default List<List<Operation>> partition(List<Operation> history){
        List<List<Operation>> temp = new ArrayList<>();
        temp.add(history);
        return temp;
    }
    default List<List<Event>> partitionEvent(List<Event> history){
        List<List<Event>> temp = new ArrayList<>();
        temp.add(history);
        return temp;
    }
    Object init();
    Pair<Boolean, Object> step(Object state, Object input, Object output);
    default boolean equal(Object state1, Object state2){
        return state1.equals(state2);
    }
    default String describeOperation(Object input, Object output){
        return String.format("%s -> %s", input.toString(), output.toString());
    };
    default String describeState(Object state){
        return String.format("%s", state.toString());
    }

}
