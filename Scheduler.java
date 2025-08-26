import java.util.*;

public class Scheduler {
    private final DataManager dataManager;
    private final Random random = new Random();

    // ... (參數定義維持不變) ...
    private final int startTime;
    private final int maxRegularTime;
    private final int maxOvertime;
    private final int transitionTime;
    private static final double INITIAL_ACCEPTANCE_RATE = 0.95;
    private static final double FINAL_TEMPERATURE = 0.01;
    private static final double TH = 0.6;
    private static final double TL = 0.2;
    private static final double ALPHA = 1.2;
    private static final double BETA = 1.5;

    public Scheduler(DataManager dataManager) {
        this.dataManager = dataManager;
        Map<String, Integer> args = dataManager.getArguments();
        this.startTime = args.getOrDefault("startTime", 510);
        this.maxRegularTime = args.getOrDefault("maxRegularTime", 540);
        this.maxOvertime = args.getOrDefault("maxOvertime", 120);
        this.transitionTime = args.getOrDefault("transitionTime", 45);
    }

    // 【核心修改】方法回傳包含初始解與最佳解的 Map
    public Map<String, Schedule> schedule() {
        List<SurgeryNode> surgeryNodes = dataManager.getSurgeryNodes();
        if (surgeryNodes.isEmpty()) {
            System.out.println("沒有手術資料可供排程。");
            return Collections.emptyMap();
        }

        System.out.println("開始執行模擬退火排程演算法...");

        Schedule initialSchedule = createInitialSchedule(surgeryNodes);
        calculateCost(initialSchedule);
        Schedule bestSchedule = initialSchedule.copy();
        Schedule currentSchedule = initialSchedule.copy();

        double initialTemperature = findInitialTemperature(currentSchedule);
        double currentTemperature = initialTemperature;
        System.out.println("計算出的初始溫度為: " + String.format("%.2f", initialTemperature));

        int n = surgeryNodes.size() * dataManager.getAllRooms().size();

        while (currentTemperature > FINAL_TEMPERATURE) {
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
            currentTemperature *= calculateCoolingRate(n);
        }

        System.out.println("\n排程完成。");

        // 更新 DataManager 中的資料以供最後輸出檔案
        updateDataManagerWithSchedule(bestSchedule);

        // 將初始解與最佳解打包回傳
        Map<String, Schedule> results = new HashMap<>();
        results.put("initial", initialSchedule);
        results.put("best", bestSchedule);
        return results;
    }

    // ... (createInitialSchedule, calculateCost, isValid, findInitialTemperature,
    // calculateAcceptanceRate, perturbSchedule, calculateCoolingRate 等方法維持不變) ...
    private Schedule createInitialSchedule(List<SurgeryNode> nodes) {
        Map<String, LinkedList<SurgeryNode>> roomSchedules = new HashMap<>();
        for (String room : dataManager.getAllRooms()) {
            roomSchedules.put(room, new LinkedList<>());
        }

        List<String> specialRooms = new ArrayList<>(dataManager.getSpecialRooms());
        List<String> generalRooms = new ArrayList<>(dataManager.getAllRooms());
        generalRooms.removeAll(specialRooms);

        int specialRoomIndex = 0;
        for (SurgeryNode node : nodes) {
            if ("Y".equalsIgnoreCase(node.getSpecialRoomRequirement()) && !specialRooms.isEmpty()) {
                String room = specialRooms.get(specialRoomIndex % specialRooms.size());
                roomSchedules.get(room).add(node);
                specialRoomIndex++;
            }
        }

        int generalRoomIndex = 0;
        for (SurgeryNode node : nodes) {
            if (!"Y".equalsIgnoreCase(node.getSpecialRoomRequirement())) {
                String room = generalRooms.get(generalRoomIndex % generalRooms.size());
                roomSchedules.get(room).add(node);
                generalRoomIndex++;
            }
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
                    currentTime -= transitionTime;
                }
            }
            roomUsage.put(room, currentTime);

            int regularOvertime = Math.max(0, currentTime - maxRegularTime);
            totalRegularOvertimeCost += regularOvertime;

            int overtime = Math.max(0, currentTime - (maxRegularTime + maxOvertime));
            totalOvertimeCost += overtime * 2.0;
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

    private boolean isValid(Schedule schedule) {
        Set<String> specialRooms = dataManager.getSpecialRooms();
        Map<String, LinkedList<SurgeryNode>> roomSchedules = schedule.getRoomSchedules();

        for (Map.Entry<String, LinkedList<SurgeryNode>> entry : roomSchedules.entrySet()) {
            String room = entry.getKey();
            List<SurgeryNode> surgeries = entry.getValue();
            for (SurgeryNode surgery : surgeries) {
                if ("Y".equalsIgnoreCase(surgery.getSpecialRoomRequirement()) && !specialRooms.contains(room)) {
                    return false;
                }
            }
        }
        return true;
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
            Schedule neighbor = perturbSchedule(schedule, temperature, 10000);
            calculateCost(neighbor);
            if (neighbor.getCost() - schedule.getCost() < 0 ||
                    random.nextDouble() < Math.exp(-(neighbor.getCost() - schedule.getCost()) / temperature)) {
                accepted++;
            }
        }
        return (double) accepted / total;
    }

    private Schedule perturbSchedule(Schedule currentSchedule, double temp, double initialTemp) {
        Schedule neighbor;
        int maxTries = 50;
        int tries = 0;

        while (tries < maxTries) {
            neighbor = currentSchedule.copy();
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

            if (list1.isEmpty()) {
                tries++;
                continue;
            }

            SurgeryNode targetNode;
            if (tNorm > TH) {
                targetNode = Collections.max(list1, Comparator.comparingInt(SurgeryNode::getSurgeryTime));
            } else {
                targetNode = Collections.min(list1, Comparator.comparingInt(SurgeryNode::getSurgeryTime));
            }
            list1.remove(targetNode);

            if (random.nextBoolean() && !list2.isEmpty()) {
                int indexToSwap = random.nextInt(list2.size());
                SurgeryNode nodeToSwap = list2.get(indexToSwap);
                list2.set(indexToSwap, targetNode);
                list1.add(nodeToSwap);
            } else {
                int insertIndex = list2.isEmpty() ? 0 : random.nextInt(list2.size() + 1);
                list2.add(insertIndex, targetNode);
            }

            if (isValid(neighbor)) {
                return neighbor;
            }
            tries++;
        }

        return currentSchedule;
    }

    private double calculateCoolingRate(int n) {
        return (ALPHA * Math.sqrt(n) - 1) / (ALPHA * Math.sqrt(n));
    }

    private void updateDataManagerWithSchedule(Schedule finalSchedule) {
        List<String[]> newTimetableData = new ArrayList<>();
        List<String[]> originalTimetable = dataManager.getTimetableData();

        if (!originalTimetable.isEmpty()) {
            newTimetableData.add(originalTimetable.get(0));
        }

        Map<String, LinkedList<SurgeryNode>> roomSchedules = finalSchedule.getRoomSchedules();

        Map<String, String[]> originalDataMap = new HashMap<>();
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
                    newRow[5] = roomName;
                    newTimetableData.add(newRow);
                }
            }
        }
        dataManager.updateTimetableData(newTimetableData);
    }
}