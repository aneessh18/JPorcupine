package aneesh18.io;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

record SampleInput(boolean op, int value){}
class JPorcupineTest {

    JPorcupine jPorcupine = new JPorcupine();
    Model sampleModel = new Model() {
        @Override
        public Object init() {
            return 0;
        }

        @Override
        public Pair<Boolean, Object> step(Object state, Object input, Object output) {
            var registerInput = (SampleInput) input;
            if(registerInput.op() == false){
                return Pair.of(true, registerInput.value());
            } else{
                var readCorrectValue = output == state;
                return Pair.of(readCorrectValue, state);
            }

        }
        @Override
        public String describeOperation(Object input, Object output) {
           var x = (SampleInput) input;
            if (!x.op()) {
                return String.format("Put(%s)", ((SampleInput) input).value());
            } else {
                return String.format("Get() -> %s", output);
            }
        }
    };
    @Test
    void testPositiveSampleModel(){
        var ops = List.of(
                new Operation(0, new SampleInput(false, 100), 0, 0, 100),
                new Operation(1, new SampleInput(true, 0), 25, 100, 75),
                new Operation(2, new SampleInput(true, 0), 30, 0, 60)

        );
        assertTrue(jPorcupine.checkOperations(sampleModel, ops));

        var events = List.of(
                new Event(0, EventKind.CALL_EVENT, new SampleInput(false, 100), 0),
                new Event(1, EventKind.CALL_EVENT, new SampleInput(true, 0), 1),
                new Event(2, EventKind.CALL_EVENT, new SampleInput(true, 0), 2),
                new Event(2, EventKind.RETURN_EVENT, 0, 2),
                new Event(1, EventKind.RETURN_EVENT, 100, 1),
                new Event(0, EventKind.RETURN_EVENT, 0, 0)
        );
        assertTrue(jPorcupine.checkEvents(sampleModel, events));

    }

    @Test
    void testNegativeSampleModel(){
        var ops = List.of(
                new Operation(0, new SampleInput(false, 200), 0, 0, 100),
                new Operation(1, new SampleInput(true, 0), 10, 200, 30),
                new Operation(2, new SampleInput(true, 0), 40, 0, 90)

        );
        assertFalse(jPorcupine.checkOperations(sampleModel, ops));

        var events = List.of(
                new Event(0, EventKind.CALL_EVENT, new SampleInput(false, 200), 0),
                new Event(1, EventKind.CALL_EVENT, new SampleInput(true, 0), 1),
                new Event(1, EventKind.RETURN_EVENT, 200, 1),
                new Event(2, EventKind.CALL_EVENT, new SampleInput(true, 0), 2),
                new Event(2, EventKind.RETURN_EVENT, 0, 2),
                new Event(0, EventKind.RETURN_EVENT, 0, 0)
        );
        assertFalse(jPorcupine.checkEvents(sampleModel, events));
    }
    @Test
    void checkOperations() {
    }

    @Test
    void checkEvents() {
    }
}