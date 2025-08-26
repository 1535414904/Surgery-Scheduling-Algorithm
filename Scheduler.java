import java.util.*;

public class Scheduler {
    private final DataManager dataManager;
    private final Random random = new Random();

    // 從 Arguments4Exec.csv 讀取的參數
    private final int startTime;
    private final int maxRegularTime;
    private final int maxOvertime;
    private final int transitionTime;

    // 模擬退火超參數
    private static final double INITIAL_ACCEPTANCE_RATE = 0.95;
    private static final double FINAL_TEMPERATURE = 0.01;
    private static final double TH = 0.6; // 高溫階段閾值
    private static final double TL = 0.2; // 低溫階段閾值
    private static final double ALPHA = 1.2; // 冷卻係數調整因子
    private static final double BETA = 1.5; // 擾動次數調整係數

    public Scheduler(DataManager dataManager) {
        this.dataManager = dataManager;
        Map<String, Integer> args = dataManager.getArguments();
        this.startTime = args.getOrDefault("startTime", 510);
        this.maxRegularTime = args.getOrDefault("maxRegularTime", 540);
        this.maxOvertime = args.getOrDefault("maxOvertime", 120);
        this.transitionTime = args.getOrDefault("transitionTime", 45);
    }

    public void schedule() {
        List<SurgeryNode> surgeryNodes = dataManager.getSurgeryNodes();
        if (surgeryNodes.isEmpty()) {
            System.out.println("沒有手術資料可供排程。");
            return;
        }

        System.out.println("開始執行模擬退火排程演算法...");

        // 1. 產生初始解
        Schedule bestSchedule = createInitialSchedule(surgeryNodes);
        calculateCost(bestSchedule);
        Schedule currentSchedule = bestSchedule.copy();

        // 2. 決定初始溫度 (二分搜尋法)
        double initialTemperature = findInitialTemperature(currentSchedule);
        double currentTemperature = initialTemperature;
        System.out.println("計算出的初始溫度為: " + String.format("%.2f", initialTemperature));

        // 問題規模 n
        int n = surgeryNodes.size() * dataManager.getAllRooms().size();

        // 3. 模擬退火主循環
        while (currentTemperature > FINAL_TEMPERATURE) {
            // 根據問題規模決定擾動次數
            int iterations = (int) Math.round(BETA * n);

            for (int i = 0; i < iterations; i++) {
                Schedule neighborSchedule = perturbSchedule(currentSchedule, currentTemperature, initialTemperature);
                calculateCost(neighborSchedule);

                double deltaE = neighborSchedule.getCost() - currentSchedule.getCost();

                if (deltaE < 0 || random.nextDouble() < Math.exp(-deltaE / currentTemperature)) {
                    currentSchedule = neighborSchedule;
                    if (currentSchedule.getCost() < bestSchedule.getCost()) {
                        bestSchedule = currentSchedule.copy();
                    }
                }
            }
            // 4. 冷卻
            currentTemperature *= calculateCoolingRate(n);
        }

        System.out.println("排程完成。");
        System.out.println("初始成本: " + String.format("%.2f", calculateCost(createInitialSchedule(surgeryNodes))));
        System.out.println("最佳成本: " + String.format("%.2f", bestSchedule.getCost()));

        // 5. 更新 DataManager 中的資料以供輸出
        updateDataManagerWithSchedule(bestSchedule);
    }

    private Schedule createInitialSchedule(List<SurgeryNode> nodes) {
        Map<String, LinkedList<SurgeryNode>> roomSchedules = new HashMap<>();
        for (String room : dataManager.getAllRooms()) {
            roomSchedules.put(room, new LinkedList<>());
        }

        List<String> roomList = new ArrayList<>(dataManager.getAllRooms());
        for (int i = 0; i < nodes.size(); i++) {
            roomSchedules.get(roomList.get(i % roomList.size())).add(nodes.get(i));
        }
        return new Schedule(roomSchedules);
    }

    private double calculateCost(Schedule schedule) {
        Map<String, LinkedList<SurgeryNode>> roomSchedules = schedule.getRoomSchedules();
        Map<String, Integer> roomUsage = new HashMap<>();

        double totalOvertimeCost = 0;
        double totalRegularOvertimeCost = 0;

        for (String room : dataManager.getAllRooms()) {
            int currentTime = 0;
            List<SurgeryNode> surgeries = roomSchedules.get(room);
            if (surgeries != null) {
                for (SurgeryNode surgery : surgeries) {
                    currentTime += surgery.getSurgeryTime() + transitionTime;
                }
                if (!surgeries.isEmpty()) {
                    currentTime -= transitionTime; // 最後一檯手術後無須銜接時間
                }
            }
            roomUsage.put(room, currentTime);

            int regularOvertime = Math.max(0, currentTime - maxRegularTime);
            totalRegularOvertimeCost += regularOvertime;

            int overtime = Math.max(0, currentTime - (maxRegularTime + maxOvertime));
            totalOvertimeCost += overtime * 2.0; // 超時成本權重設為2
        }

        double totalUsage = roomUsage.values().stream().mapToInt(Integer::intValue).sum();
        double avgUsage = totalUsage / (double) dataManager.getAllRooms().size();

        double balanceCost = 0;
        for (int usage : roomUsage.values()) {
            balanceCost += Math.abs(usage - avgUsage);
        }

        double finalCost = totalRegularOvertimeCost + totalOvertimeCost + balanceCost;
        schedule.setCost(finalCost);
        return finalCost;
    }

    private double findInitialTemperature(Schedule initialSchedule) {
        double low = 0.01, high = 10000;
        double mid;
        while (high - low > 0.1) {
            mid = low + (high - low) / 2.0;
            if (calculateAcceptanceRate(initialSchedule, mid) < INITIAL_ACCEPTANCE_RATE) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    private double calculateAcceptanceRate(Schedule schedule, double temperature) {
        int accepted = 0;
        int total = 100;
        for (int i = 0; i < total; i++) {
            Schedule neighbor = perturbSchedule(schedule, temperature, 10000); // 使用高溫來測試
            calculateCost(neighbor);
            if (neighbor.getCost() - schedule.getCost() < 0 ||
                    random.nextDouble() < Math.exp(-(neighbor.getCost() - schedule.getCost()) / temperature)) {
                accepted++;
            }
        }
        return (double) accepted / total;
    }

    private Schedule perturbSchedule(Schedule currentSchedule, double temp, double initialTemp) {
        Schedule neighbor = currentSchedule.copy();
        Map<String, LinkedList<SurgeryNode>> schedules = neighbor.getRoomSchedules();
        List<String> rooms = new ArrayList<>(schedules.keySet());

        if (rooms.isEmpty() || rooms.size() < 2)
            return neighbor;

        double tNorm = (temp - FINAL_TEMPERATURE) / (initialTemp - FINAL_TEMPERATURE);

        String room1Name = rooms.get(random.nextInt(rooms.size()));
        String room2Name = rooms.get(random.nextInt(rooms.size()));
        while (room1Name.equals(room2Name)) {
            room2Name = rooms.get(random.nextInt(rooms.size()));
        }

        LinkedList<SurgeryNode> list1 = schedules.get(room1Name);
        LinkedList<SurgeryNode> list2 = schedules.get(room2Name);

        if (list1.isEmpty())
            return neighbor;

        SurgeryNode targetNode;
        if (tNorm > TH) { // 高溫階段: 移動長時間手術
            targetNode = Collections.max(list1, Comparator.comparingInt(SurgeryNode::getSurgeryTime));
        } else { // 中低溫階段: 移動短時間手術
            targetNode = Collections.min(list1, Comparator.comparingInt(SurgeryNode::getSurgeryTime));
        }
        list1.remove(targetNode);

        if (random.nextBoolean()) { // 50% 機率交換，50% 機率插入
            if (!list2.isEmpty()) {
                int indexToSwap = random.nextInt(list2.size());
                SurgeryNode nodeToSwap = list2.get(indexToSwap);
                list2.set(indexToSwap, targetNode);
                list1.add(nodeToSwap);
            } else {
                list2.add(targetNode);
            }
        } else {
            int insertIndex = list2.isEmpty() ? 0 : random.nextInt(list2.size() + 1);
            list2.add(insertIndex, targetNode);
        }

        return neighbor;
    }

    private double calculateCoolingRate(int n) {
        return (ALPHA * Math.sqrt(n) - 1) / (ALPHA * Math.sqrt(n));
    }

    private void updateDataManagerWithSchedule(Schedule finalSchedule) {
        List<String[]> newTimetableData = new ArrayList<>();
        List<String[]> originalTimetable = dataManager.getTimetableData();

        // 保持原始表頭
        if (!originalTimetable.isEmpty()) {
            newTimetableData.add(originalTimetable.get(0));
        }

        Map<String, LinkedList<SurgeryNode>> roomSchedules = finalSchedule.getRoomSchedules();

        // 創建一個從 application ID 到原始手術資料行的映射
        Map<String, String[]> originalDataMap = new HashMap<>();
        // 從 1 開始跳過表頭
        for (int i = 1; i < originalTimetable.size(); i++) {
            String[] row = originalTimetable.get(i);
            if (row.length > 1) {
                originalDataMap.put(row[1], row);
            }
        }

        for (Map.Entry<String, LinkedList<SurgeryNode>> entry : roomSchedules.entrySet()) {
            String roomName = entry.getKey();
            List<SurgeryNode> surgeries = entry.getValue();

            for (SurgeryNode surgery : surgeries) {
                String[] originalRow = originalDataMap.get(surgery.getApplicationId());
                if (originalRow != null) {
                    String[] newRow = Arrays.copyOf(originalRow, originalRow.length);
                    newRow[5] = roomName; // 更新手術房
                    newTimetableData.add(newRow);
                }
            }
        }
        dataManager.updateTimetableData(newTimetableData);
    }
}