package aneesh18.io;

import java.util.List;

public class JPorcupine {
    private final Checker checker = new Checker();
    public boolean checkOperations(Model model, List<Operation> history){
        return checker.checkOperations(model, history, false, 60).getLeft() == CheckResult.OK;
    }
    public CheckResult CheckOperationsTimeout(Model model, List<Operation> history, int timeout){
        return checker.checkOperations(model, history, false, timeout).getLeft();
    }
    public boolean checkEvents(Model model, List<Event> history){
        return checker.checkEvents(model, history, false, 60).getLeft() == CheckResult.OK;
    }


}
