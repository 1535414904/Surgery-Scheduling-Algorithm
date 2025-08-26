import java.util.*;
import java.util.stream.Collectors;

// 新增的類別，專門用於計算和儲存排程的統計指標
public class ScheduleMetrics {
    // 公開的統計數據欄位
    public final double cost;
    public final long totalSurgeryTime;
    public final long totalUsageTime;
    public final long totalTransitionTime;
    public final long totalRegularOvertime;
    public final long totalOvertime;
    public final boolean isSpecialRoomRequirementMet;
    public final Map<String, RoomMetrics> roomMetrics;

    // 內部類別，儲存單一手術房的指標
    public static class RoomMetrics {
        public final int usageTime;
        public final int regularOvertime;
        public final int overtime;

        public RoomMetrics(int usageTime, int regularOvertime, int overtime) {
            this.usageTime = usageTime;
            this.regularOvertime = regularOvertime;
            this.overtime = overtime;
        }
    }

    public ScheduleMetrics(Schedule schedule, DataManager dataManager) {
        Map<String, LinkedList<SurgeryNode>> roomSchedules = schedule.getRoomSchedules();
        Set<String> specialRooms = dataManager.getSpecialRooms();
        Map<String, Integer> args = dataManager.getArguments();

        int maxRegularTime = args.getOrDefault("maxRegularTime", 540);
        int maxOvertime = args.getOrDefault("maxOvertime", 120);
        int transitionTime = args.getOrDefault("transitionTime", 45);

        long tempTotalSurgeryTime = 0;
        long tempTotalTransitionTime = 0;
        long tempTotalRegularOvertime = 0;
        long tempTotalOvertime = 0;
        boolean specialMet = true;

        this.roomMetrics = new TreeMap<>(); // 使用 TreeMap 自動排序手術房名稱

        for (String room : dataManager.getAllRooms()) {
            int roomSurgeryTime = 0;
            int roomTransitionTime = 0;

            List<SurgeryNode> surgeries = roomSchedules.get(room);
            if (surgeries != null && !surgeries.isEmpty()) {
                for (SurgeryNode surgery : surgeries) {
                    roomSurgeryTime += surgery.getSurgeryTime();
                    // 檢查特殊手術房需求
                    if ("Y".equalsIgnoreCase(surgery.getSpecialRoomRequirement()) && !specialRooms.contains(room)) {
                        specialMet = false;
                    }
                }
                roomTransitionTime = (surgeries.size() - 1) * transitionTime;
            }

            int roomUsageTime = roomSurgeryTime + roomTransitionTime;
            int regularOvertime = Math.max(0, roomUsageTime - maxRegularTime);
            int overtime = Math.max(0, roomUsageTime - (maxRegularTime + maxOvertime));

            tempTotalSurgeryTime += roomSurgeryTime;
            tempTotalTransitionTime += roomTransitionTime;
            tempTotalRegularOvertime += regularOvertime;
            tempTotalOvertime += overtime;

            this.roomMetrics.put(room, new RoomMetrics(roomUsageTime, regularOvertime, overtime));
        }

        this.totalSurgeryTime = tempTotalSurgeryTime;
        this.totalTransitionTime = tempTotalTransitionTime;
        this.totalUsageTime = this.totalSurgeryTime + this.totalTransitionTime;
        this.totalRegularOvertime = tempTotalRegularOvertime;
        this.totalOvertime = tempTotalOvertime;
        this.isSpecialRoomRequirementMet = specialMet;
        this.cost = schedule.getCost();
    }
}