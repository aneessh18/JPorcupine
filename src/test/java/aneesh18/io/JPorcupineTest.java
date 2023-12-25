package aneesh18.io;

import org.apache.commons.lang3.ClassLoaderUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

record SampleInput(boolean op, int value){}

record EtcdInput(byte op, int arg1, int arg2 ){}

record EtcdOutput(boolean ok, boolean exists, int value, boolean unknown ){}
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

    Model etcdModel = new Model() {
        @Override
        public Object init() {
            return -1000000;
        }

        @Override
        public Pair<Boolean, Object> step(Object state, Object input, Object output) {
            var st = (int) state;
            var inp = (EtcdInput) input;
            var out = (EtcdOutput) output;
            if (inp.op() == 0){
                var ok = (!out.exists() && st ==  -1000000)  || (out.exists() && st == out.value()) || out.unknown();
                return Pair.of(ok, state);
            } else if(inp.op() == 1) {
                return Pair.of(true, inp.arg1());
            } else{
                var ok = (inp.arg1() == st && out.ok()) || (inp.arg1() != st && !out.ok()) || out.unknown();
                var result = st;
                if (inp.arg1() == st)
                    result = inp.arg2();
                return Pair.of(ok, result);
            }
        }
        @Override
        public String describeOperation(Object input, Object output){
            var inp = (EtcdInput) input;
            var out = (EtcdOutput) output;
            if (inp.op() == 0){
                return String.format("read() -> %s", out.exists()? out.value():"null");
            } else if(inp.op() == 1){
                return String.format("Write(%s)", inp.arg1());
            } else {
                String ret;
                if(out.unknown()){
                    ret = "unknown";
                } else if (out.ok()){
                    ret = "ok";
                } else {
                    ret = "fail";
                }
                return String.format("cas(%s, %s) -> %s", inp.arg1(), inp.arg2(), ret);
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

    List<Event> parseJepsenLog(String filename) throws URISyntaxException, IOException {
        URL url = getClass().getClassLoader().getResource(filename);
        List<String> lines = Files.lines(Paths.get(url.toURI())).collect(Collectors.toList());
        var invokeRead = Pattern.compile("INFO\\s+jepsen\\.util\\s+-\\s+(\\d+)\\s+:invoke\\s+:read\\s+nil$");
        var invokeWrite =  Pattern.compile("INFO\\s+jepsen\\.util\\s+-\\s+(\\d+)\\s+:invoke\\s+:write\\s+(\\d+)$");
        var invokeCas = Pattern.compile("INFO\\s+jepsen\\.util\\s+-\\s+(\\d+)\\s+:invoke\\s+:cas\\s+\\[(\\d+)\\s+(\\d+)\\]$");
        var returnRead = Pattern.compile("INFO\\s+jepsen\\.util\\s+-\\s+(\\d+)\\s+:ok\\s+:read\\s+(nil|\\d+)$");
        var returnWrite = Pattern.compile("INFO\\s+jepsen\\.util\\s+-\\s+(\\d+)\\s+:ok\\s+:write\\s+(\\d+)$");
        var returnCas = Pattern.compile("INFO\\s+jepsen\\.util\\s+-\\s+(\\d+)\\s+:(ok|fail)\\s+:cas\\s+\\[(\\d+)\\s+(\\d+)\\]$");
        var timeoutRead = Pattern.compile("INFO\\s+jepsen\\.util\\s+-\\s+(\\d+)\\s+:fail\\s+:read\\s+:timed-out$");

        List<Event> events = new ArrayList<>();
        Map<Integer, Integer> procIdMap = new HashMap<>();
       int id = 0;
       for(var line: lines){
           Matcher invokeReadMatcher = invokeRead.matcher(line);
           Matcher invokeWriteMatcher = invokeWrite.matcher(line);
           Matcher invokeCasMatcher = invokeCas.matcher(line);
           Matcher returnReadMatcher = returnRead.matcher(line);
           Matcher returnWriteMatcher = returnWrite.matcher(line);
           Matcher returnCasMatcher = returnCas.matcher(line);
           Matcher timeoutReadMatcher = timeoutRead.matcher(line);

           if(invokeReadMatcher.find()){
               var proc = Integer.parseInt(invokeReadMatcher.group(1));
               events.add(new Event(
                       proc,
                       EventKind.CALL_EVENT,
                       new EtcdInput((byte) 0, 0, 0),
                       id
               ));
               procIdMap.put(proc, id);
               id++;
           }
           else if (invokeWriteMatcher.find()){
                var proc = Integer.parseInt(invokeWriteMatcher.group(1));
                var value = invokeWriteMatcher.group(2);
                events.add(new Event(
                        proc,
                        EventKind.CALL_EVENT,
                        new EtcdInput(
                                (byte) 1,
                                Integer.parseInt(value),
                                0
                        ),
                        id
                ));
                procIdMap.put(proc, id);
                id++;
           }
           else if(invokeCasMatcher.find()){
                var proc = Integer.parseInt(invokeCasMatcher.group(1));
                var from =  Integer.parseInt(invokeCasMatcher.group(2));
                var to = Integer.parseInt(invokeCasMatcher.group(3));
                events.add(
                        new Event(
                                proc,
                                EventKind.CALL_EVENT,
                                new EtcdInput((byte)2, from, to)
                                ,id
                        )
                );
                procIdMap.put(proc, id);
                id++;
           }
           else if (returnReadMatcher.find()) {
                var proc = Integer.parseInt(returnReadMatcher.group(1));
                int val = -1;
                if(returnReadMatcher.groupCount()>=2 && !returnReadMatcher.group(2).equals("nil")){
                    val = Integer.parseInt(returnReadMatcher.group(2));
                }
                var matchId = procIdMap.get(proc);
                procIdMap.remove(proc);
                events.add(
                        new Event(
                                proc,
                                EventKind.RETURN_EVENT,
                                new EtcdOutput(
                                        false,
                                        val != -1,
                                        val,
                                        false
                                ),
                                matchId
                        )
                );
           }
           else if (returnWriteMatcher.find()) {
                var proc = Integer.parseInt(returnWriteMatcher.group(1));
                var matchId = procIdMap.get(proc);
                procIdMap.remove(proc);
                events.add(
                        new Event(
                                proc,
                                EventKind.RETURN_EVENT,
                                new EtcdOutput(
                                false, false, 1, false
                                ),
                                matchId
                        )
                );
           }
           else if (returnCasMatcher.find()) {
                var proc = Integer.parseInt(returnCasMatcher.group(1));
                var matchId = procIdMap.get(proc);
                procIdMap.remove(proc);
                events.add(
                        new Event(proc, EventKind.RETURN_EVENT, new EtcdOutput(
                                returnCasMatcher.group(2).equals("ok"),
                                false, 0, false
                        ), matchId)
                );
           }
           else if (timeoutReadMatcher.find()){
                var proc = Integer.parseInt(timeoutReadMatcher.group(1));
                var matchId = procIdMap.get(proc);
                procIdMap.remove(proc);
                events.add(
                        new Event(
                                proc,
                                EventKind.RETURN_EVENT,
                                new EtcdOutput(
                                        false, false, -1, true
                                ),
                                matchId
                        )
                );
           }
       }
       for (var proc: procIdMap.keySet()){
           events.add(new Event(proc, EventKind.RETURN_EVENT, new EtcdOutput(
                   false, false, -1, true
           ), procIdMap.get(proc)
           ));
       }
       return events;
    }
    void checkJepsen(String logNum, boolean result) throws URISyntaxException, IOException {
        List<Event> events = parseJepsenLog(String.format("jepsen/etcd_%s.log", logNum));
        assertEquals(result, jPorcupine.checkEvents(etcdModel, events));
    }

    @Test
    void testEtcdJepsen000() throws URISyntaxException, IOException {
        checkJepsen("000", false);
    }
}