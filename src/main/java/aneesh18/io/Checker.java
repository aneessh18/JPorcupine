package aneesh18.io;

import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static aneesh18.io.EntryKind.CALL_ENTRY;
import static aneesh18.io.EntryKind.RETURN_ENTRY;

enum EntryKind{
    CALL_ENTRY,
    RETURN_ENTRY
}
record Entry(
        EntryKind kind,
        Object value,
        int id,
        long time,
        int clientId){}

record LinearizationInfo (
        List<List<Entry>> history,
        Integer[][][] partialLinearizations
){}

record ByTime( List<Entry> entries){
}

// DLL.
class Node {
    Object value;
    Node match, next, prev;
    int id;


     static Node insertBefore(Node n, Node mark) {
        if (mark != null) {
            var beforeMark = mark.prev;
            mark.prev = n;
            n.next = mark;
            if (beforeMark != null) {
                n.prev = beforeMark;
                beforeMark.next = n;
            }
        }
        return n;
    }

     static int length(Node n) {
        var l = 0;
        while (n != null) {
            n = n.next;
            l++;
        }
        return l;
    }
}

record CacheEntry(BitSet linearized, Object state){}

record CallsEntry (Node entry, Object state){}
public class Checker {

    private List<Entry> makeEntries(List<Operation> history){
        List<Entry> entries = new ArrayList<Entry>();

        for(int id=0; id< history.size(); id++){
            entries.add(new Entry(
                    CALL_ENTRY,
                    history.get(id).input(),
                    id,
                    history.get(id).start(),
                    history.get(id).clientId()
            ));
            entries.add(new Entry(
                    EntryKind.RETURN_ENTRY,
                    history.get(id).output(),
                    id,
                    history.get(id).end(),
                    history.get(id).clientId()
            ));
        }
        entries.sort((a, b) -> {
            if (a.time() != b.time()){
                return Long.compare(a.time() , b.time());
            }

            return a.kind() == CALL_ENTRY && b.kind() == EntryKind.RETURN_ENTRY ? -1: 0;
        });
        return entries;
    }

    private List<Event> renumber(List<Event> events){
        List<Event> e = new ArrayList<>();
        var m = new HashMap<Integer, Integer>();
        var id = 0;
        for(Event v: events){
            if(m.containsKey(v.id())){
                e.add(new Event(v.clientId(), v.kind(), v.value(), m.get(v.id())));
            } else{
                e.add(new Event(v.clientId(), v.kind(), v.value(), id));
                m.put(v.id(), id);
                id++;
            }
        }
        return e;
    }

    private List<Entry> convertEntries(List<Event> events){
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            var kind = CALL_ENTRY;
            if(events.get(i).kind() == EventKind.RETURN_EVENT){
                kind = RETURN_ENTRY;
            }
            entries.add(new Entry(kind, events.get(i).value(), events.get(i).id(), i, events.get(i).clientId()));
        }
        return entries;
    }

    private Node makeLinkedEntries(List<Entry> entries){
        Node root = null;
        Map<Integer, Node> match = new HashMap<>();
        for(int i = entries.size()-1; i>=0;i--){
            var elem = entries.get(i);
            if(elem.kind() == RETURN_ENTRY){
                var entry = new Node();
                entry.value = elem.value();
                entry.match = null;
                entry.id = elem.id();
                match.put(elem.id(), entry);
                Node.insertBefore(entry, root);
                root = entry;
            } else{
                var entry = new Node();
                entry.value = elem.value();
                entry.match = match.get(elem.id());
                entry.id = elem.id();
                Node.insertBefore(entry, root);
                root = entry;
            }
        }
        return root;
    }

    private boolean cacheContains(Model model, Map<Integer, List<CacheEntry>> cache, CacheEntry entry ){
        if(cache.containsKey(entry.linearized().hashCode())){
            for (CacheEntry elem: cache.get(entry.linearized().hashCode())) {
                if( entry.linearized().equals(elem.linearized()) && model.equal(entry.state(), elem.state())){
                    return true;
                }
            }
        }

        return false;
    }

    private void lift(Node entry){
        entry.prev.next = entry.next;
        entry.next.prev = entry.prev;
        var match = entry.match;
        match.prev.next = match.next;
        if(match.next != null){
            match.next.prev = match.prev;
        }
    }

    private void unLift(Node entry){
        var match = entry.match;
        match.prev.next = match;
        if(match.next != null){
            match.next.prev = match;
        }
    }

    private Pair<Boolean, Integer[][]> checkSingle(Model model, List<Entry> history, boolean computePartial) {
        var entry = makeLinkedEntries(history);
        var n = Node.length(entry)/2;
        var linearized = new BitSet(n%64 == 0 ? n:n+1);
        Map<Integer, List<CacheEntry>> cache = new HashMap<>();
        List<CallsEntry> calls = new ArrayList<>();
        Integer[][] longest = new Integer[n][];

        var state = model.init();
        Node temp = new Node();
        temp.value= null;
        temp.match = null;
        temp.id = -1;
        var headEntry = Node.insertBefore(temp, entry);
        while(headEntry.next != null){
            if(entry.match != null){
                var matching = entry.match;
                Pair<Boolean, Object> result = model.step(state, entry.value, matching.value);
                if(result.getLeft()){
                    var newLinearized = ((BitSet)linearized.clone());
                    newLinearized.set(entry.id);
                    var newCacheEntry = new CacheEntry(newLinearized, result.getRight());
                    if(!cacheContains(model, cache, newCacheEntry)){
                        var hash = newLinearized.hashCode();
                        cache.putIfAbsent(hash, new ArrayList<>());
                        cache.get(hash).add(newCacheEntry);
                        calls.add(new CallsEntry(entry, state));
                        state = result.getRight();
                        linearized.set(entry.id);
                        lift(entry);
                        entry = entry.next;
                    } else{
                        entry = entry.next;
                    }
                } else {
                    entry = entry.next;
                }
            } else{
                if(calls.isEmpty()){
                    return Pair.of(false, longest);
                }
                if(computePartial) {
                    var callsSize = calls.size();
                    Integer[] seq = null;
                    for (CallsEntry v: calls){
                        if (longest[v.entry().id] == null || callsSize > longest[v.entry().id].length){
                            if(seq == null){
                                seq = new Integer[calls.size()];
                                for(int i=0;i<calls.size(); i++){
                                    seq[i] = (v.entry().id);
                                }
                            }
                            longest[v.entry().id] = seq;
                        }
                    }
                }
                var callsTop = calls.get(calls.size()-1);
                entry = callsTop.entry();
                state = callsTop.state();
                linearized.clear(entry.id);
                calls.remove(calls.size()-1);
                unLift(entry);
                entry = entry.next;
            }
        }
        Integer[] seq = new Integer[calls.size()];
        for (int i=0;i<calls.size();i++){
            seq[i] = (calls.get(i).entry().id);
        }
        Arrays.fill(longest, (seq));
        return Pair.of(true, longest);
    }

    private Pair<CheckResult, LinearizationInfo> checkParallel(Model model, List<List<Entry>> history, boolean computeInfo, int timeout){

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        var ok = true;
        boolean timedOut = false;
        var longest = new Integer[history.size()][][];
         BlockingQueue<Future<Boolean>> results = new ArrayBlockingQueue<>(history.size());
        for (int i = 0;i< history.size();i++) {
            final var subHistory = history.get(i);
            final var finalI = i;
            results.add(executorService.submit(() ->
            {
                var r = checkSingle(model, subHistory, computeInfo);
                longest[finalI] = r.getRight();
                return r.getLeft();
            }));
        }
        // wait for all tasks get completed
        for (var result: results){
            try {
                var r = result.get(timeout, TimeUnit.SECONDS);
                ok = ok && r;
            } catch (TimeoutException e) {
                timedOut = true;
                System.out.println("Task Timed out");
            } catch (Exception e) {
                System.out.println("Task Failed with message "+ e.getMessage());
            }
        }
        LinearizationInfo info = null;

        if (computeInfo){
            var partialLinearizations = new Integer[history.size()][][];
            for(int i=0;i< history.size();i++){
                var set = new HashMap<Integer[], Object>();
                for(var v: longest[i]){
                    if(v!=null){
                        set.put(v,new Object());
                    }
                }
                Integer[][] partials = new Integer[set.size()][];
                int c = 0;
                for (var k:set.keySet()){
                    partials[c++] = Arrays.copyOf(k, k.length);
                }
                partialLinearizations[i] = partials;
            }
            info = new LinearizationInfo(history, partialLinearizations);
        }
       CheckResult checkResult;
        if(!ok){
            checkResult = CheckResult.ILLEGAL;
        } else if (timedOut){
            checkResult = CheckResult.UNKNOWN;
        } else {
            checkResult = CheckResult.OK;
        }
        return Pair.of(checkResult, info);
    }

    Pair<CheckResult, LinearizationInfo> checkEvents(Model model, List<Event> history, boolean verbose, int timeout){
        var partitions = model.partitionEvent(history);
        List<List<Entry>> l = new ArrayList<>();
        for (List<Event> subHistory : partitions) {
            l.add(convertEntries(renumber(subHistory)));
        }
        return checkParallel(model, l, verbose, timeout);
    }

     Pair<CheckResult, LinearizationInfo> checkOperations(Model model, List<Operation> history, boolean verbose, int timeout ){
        var partitions = model.partition(history);
        List<List<Entry>> l = new ArrayList<>();
        for (List<Operation> partition : partitions) {
            l.add(makeEntries(partition));
        }
        return checkParallel(model, l, verbose, timeout);
    }
}
