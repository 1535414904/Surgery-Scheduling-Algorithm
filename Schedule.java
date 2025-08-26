import java.util.*;

public class Schedule {
    private final Map<String, LinkedList<SurgeryNode>> roomSchedules;
    private double cost;

    public Schedule(Map<String, LinkedList<SurgeryNode>> roomSchedules) {
        this.roomSchedules = roomSchedules;
        this.cost = 0.0;
    }

    public Map<String, LinkedList<SurgeryNode>> getRoomSchedules() {
        return roomSchedules;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    // 複製排程以產生鄰近解
    public Schedule copy() {
        Map<String, LinkedList<SurgeryNode>> newSchedules = new HashMap<>();
        for (Map.Entry<String, LinkedList<SurgeryNode>> entry : this.roomSchedules.entrySet()) {
            newSchedules.put(entry.getKey(), new LinkedList<>(entry.getValue()));
        }
        Schedule newSchedule = new Schedule(newSchedules);
        newSchedule.setCost(this.cost);
        return newSchedule;
    }
}