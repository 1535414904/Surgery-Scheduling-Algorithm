import java.util.List;

public class Scheduler {
    private final DataManager dataManager;

    public Scheduler(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void schedule() {
        List<SurgeryNode> surgeryNodes = dataManager.getSurgeryNodes();
        // TODO: 在此處實現排程演算法
        System.out.println("排程演算法尚未實現");
    }
}