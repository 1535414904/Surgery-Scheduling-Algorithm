import java.util.*;

public class Scheduler {
    private final DataManager dataManager;
    private final Random random = new Random();

    // --- 演算法參數定義 ---
    /** 排程開始時間 (分鐘) */
    private final int startTime;
    /** 每日最大常規工作時間 (分鐘) */
    private final int maxRegularTime;
    /** 每日最大加班時間 (分鐘) */
    private final int maxOvertime;
    /** 手術之間的銜接時間 (分鐘) */
    private final int transitionTime;
    /** 模擬退火: 初始接受率，用於動態計算初始溫度 */
    private static final double INITIAL_ACCEPTANCE_RATE = 0.95;
    /** 模擬退火: 終止溫度，當溫度降至此值時演算法結束 */
    private static final double FINAL_TEMPERATURE = 0.01;
    /** 模擬退火: 高溫階段的溫度門檻值 (正規化後) */
    private static final double TH = 0.6;
    /** 模擬退火: 低溫階段的溫度門檻值 (正規化後) */
    private static final double TL = 0.2;
    /** 模擬退火: 冷卻係數的調整因子 alpha */
    private static final double ALPHA = 1.2;
    /** 模擬退火: 迭代次數的調整因子 beta */
    private static final double BETA = 1.5;

    /**
     * 建構子，初始化排程器並從 DataManager 讀取所需參數
     * 
     * @param dataManager 資料管理器實例
     */
    public Scheduler(DataManager dataManager) {
        this.dataManager = dataManager;
        Map<String, Integer> args = dataManager.getArguments();
        this.startTime = args.getOrDefault("startTime", 510);
        this.maxRegularTime = args.getOrDefault("maxRegularTime", 540);
        this.maxOvertime = args.getOrDefault("maxOvertime", 120);
        this.transitionTime = args.getOrDefault("transitionTime", 45);
    }

    /**
     * 執行模擬退火演算法進行手術排程
     * 
     * @return 一個包含 "initial" (初始解) 和 "best" (最佳解) 的 Map
     */
    public Map<String, Schedule> schedule() {
        List<SurgeryNode> surgeryNodes = dataManager.getSurgeryNodes();
        if (surgeryNodes.isEmpty()) {
            System.out.println("沒有手術資料可供排程。");
            return Collections.emptyMap();
        }

        System.out.println("開始執行模擬退火排程演算法...");

        // 1. 產生初始排程解
        Schedule initialSchedule = createInitialSchedule(surgeryNodes);
        calculateCost(initialSchedule); // 計算初始解的成本

        // 2. 初始化目前解與最佳解
        Schedule bestSchedule = initialSchedule.copy();
        Schedule currentSchedule = initialSchedule.copy();

        // 3. 根據初始接受率動態計算初始溫度
        double initialTemperature = findInitialTemperature(currentSchedule);
        double currentTemperature = initialTemperature;
        System.out.println("計算出的初始溫度為: " + String.format("%.2f", initialTemperature));

        // 4. 計算問題規模 n (手術數量 * 手術房數量)
        int n = surgeryNodes.size() * dataManager.getAllRooms().size();

        // 5. 模擬退火主要迴圈，直到溫度降至終止溫度
        while (currentTemperature > FINAL_TEMPERATURE) {
            // 根據問題規模計算在當前溫度下的迭代次數
            int iterations = (int) Math.round(BETA * n);

            for (int i = 0; i < iterations; i++) {
                // 擾動目前解以產生一個鄰近解
                Schedule neighborSchedule = perturbSchedule(currentSchedule, currentTemperature, initialTemperature);
                calculateCost(neighborSchedule); // 計算鄰近解的成本

                // 計算成本差異
                double deltaE = neighborSchedule.getCost() - currentSchedule.getCost();

                // Metropolis 接受準則：
                // 如果鄰近解更好 (deltaE < 0)，則直接接受
                // 如果鄰近解較差，則以一定機率接受，避免陷入局部最佳解
                if (deltaE < 0 || random.nextDouble() < Math.exp(-deltaE / currentTemperature)) {
                    currentSchedule = neighborSchedule;
                    // 如果目前解優於歷史最佳解，則更新最佳解
                    if (currentSchedule.getCost() < bestSchedule.getCost()) {
                        bestSchedule = currentSchedule.copy();
                    }
                }
            }
            // 根據冷卻策略降低溫度
            currentTemperature *= calculateCoolingRate(n);
        }

        System.out.println("\n排程完成。");

        // 6. 將最佳排程結果更新回 DataManager 以便後續輸出
        updateDataManagerWithSchedule(bestSchedule);

        // 7. 將初始解與最佳解打包回傳
        Map<String, Schedule> results = new HashMap<>();
        results.put("initial", initialSchedule);
        results.put("best", bestSchedule);
        return results;
    }

    /**
     * 建立一個初始的排程方案
     * 策略：特殊手術房的手術優先分配到特殊房，其餘則平均分配到一般房
     * 
     * @param nodes 所有待排程的手術節點
     * @return 初始排程物件
     */
    private Schedule createInitialSchedule(List<SurgeryNode> nodes) {
        Map<String, LinkedList<SurgeryNode>> roomSchedules = new HashMap<>();
        for (String room : dataManager.getAllRooms()) {
            roomSchedules.put(room, new LinkedList<>());
        }

        List<String> specialRooms = new ArrayList<>(dataManager.getSpecialRooms());
        List<String> generalRooms = new ArrayList<>(dataManager.getAllRooms());
        generalRooms.removeAll(specialRooms);

        // 優先分配需要特殊手術房的手術
        int specialRoomIndex = 0;
        for (SurgeryNode node : nodes) {
            if ("Y".equalsIgnoreCase(node.getSpecialRoomRequirement()) && !specialRooms.isEmpty()) {
                String room = specialRooms.get(specialRoomIndex % specialRooms.size());
                roomSchedules.get(room).add(node);
                specialRoomIndex++;
            }
        }

        // 分配一般手術
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

    /**
     * 計算排程的成本函數
     * 成本 = 總加班成本 + 總超時成本 (權重為2) + 各房間使用時間的平衡成本
     * 
     * @param schedule 待計算成本的排程
     * @return 計算出的總成本
     */
    private double calculateCost(Schedule schedule) {
        Map<String, LinkedList<SurgeryNode>> roomSchedules = schedule.getRoomSchedules();
        Map<String, Integer> roomUsage = new HashMap<>();

        double totalOvertimeCost = 0;
        double totalRegularOvertimeCost = 0;

        // 計算每個房間的使用時間、加班與超時成本
        for (String room : dataManager.getAllRooms()) {
            int currentTime = 0;
            List<SurgeryNode> surgeries = roomSchedules.get(room);
            if (surgeries != null) {
                for (SurgeryNode surgery : surgeries) {
                    currentTime += surgery.getSurgeryTime() + transitionTime;
                }
                if (!surgeries.isEmpty()) {
                    currentTime -= transitionTime; // 最後一檯手術後不需銜接時間
                }
            }
            roomUsage.put(room, currentTime);

            // 計算加班成本
            int regularOvertime = Math.max(0, currentTime - maxRegularTime);
            totalRegularOvertimeCost += regularOvertime;

            // 計算超時成本 (權重加倍)
            int overtime = Math.max(0, currentTime - (maxRegularTime + maxOvertime));
            totalOvertimeCost += overtime * 2.0;
        }

        // 計算各房間使用時間的平衡成本
        double totalUsage = roomUsage.values().stream().mapToInt(Integer::intValue).sum();
        double avgUsage = totalUsage / (double) dataManager.getAllRooms().size();

        double balanceCost = 0;
        for (int usage : roomUsage.values()) {
            balanceCost += Math.abs(usage - avgUsage);
        }

        // 加總所有成本
        double finalCost = totalRegularOvertimeCost + totalOvertimeCost + balanceCost;
        schedule.setCost(finalCost);
        return finalCost;
    }

    /**
     * 檢查排程是否滿足所有限制 (例如：特殊手術是否在特殊房)
     * 
     * @param schedule 待檢查的排程
     * @return 如果排程有效則回傳 true，否則 false
     */
    private boolean isValid(Schedule schedule) {
        Set<String> specialRooms = dataManager.getSpecialRooms();
        Map<String, LinkedList<SurgeryNode>> roomSchedules = schedule.getRoomSchedules();

        for (Map.Entry<String, LinkedList<SurgeryNode>> entry : roomSchedules.entrySet()) {
            String room = entry.getKey();
            List<SurgeryNode> surgeries = entry.getValue();
            for (SurgeryNode surgery : surgeries) {
                // 檢查要求特殊房的手術是否被安排在非特殊房中
                if ("Y".equalsIgnoreCase(surgery.getSpecialRoomRequirement()) && !specialRooms.contains(room)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 使用二分搜尋法找到適合的初始溫度
     * 目標是找到一個溫度，使得初始接受率約等於 INITIAL_ACCEPTANCE_RATE
     * 
     * @param initialSchedule 初始排程
     * @return 計算出的初始溫度
     */
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

    /**
     * 在給定溫度下，計算鄰近解的接受率
     * 
     * @param schedule    目前排程
     * @param temperature 測試溫度
     * @return 接受率
     */
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

    /**
     * 根據溫度階段對目前排程進行擾動，以產生鄰近解
     * 
     * @param currentSchedule 目前的排程
     * @param temp            目前溫度
     * @param initialTemp     初始溫度
     * @return 一個新的、有效的鄰近排程
     */
    private Schedule perturbSchedule(Schedule currentSchedule, double temp, double initialTemp) {
        Schedule neighbor;
        int maxTries = 50; // 最大嘗試次數，避免無限迴圈
        int tries = 0;

        while (tries < maxTries) {
            neighbor = currentSchedule.copy();
            Map<String, LinkedList<SurgeryNode>> schedules = neighbor.getRoomSchedules();
            List<String> rooms = new ArrayList<>(schedules.keySet());

            if (rooms.isEmpty() || rooms.size() < 2)
                return neighbor;

            // 正規化溫度，用於判斷目前是高、中、低溫的哪個階段
            double tNorm = (temp - FINAL_TEMPERATURE) / (initialTemp - FINAL_TEMPERATURE);

            // 隨機選擇兩個不同的房間
            String room1Name = rooms.get(random.nextInt(rooms.size()));
            String room2Name = rooms.get(random.nextInt(rooms.size()));
            while (room1Name.equals(room2Name)) {
                room2Name = rooms.get(random.nextInt(rooms.size()));
            }

            LinkedList<SurgeryNode> list1 = schedules.get(room1Name);
            LinkedList<SurgeryNode> list2 = schedules.get(room2Name);

            if (list1.isEmpty()) {
                tries++;
                continue; // 如果來源房間是空的，就重來
            }

            SurgeryNode targetNode;
            // 高溫階段：移動手術時間最長的手術，進行大範圍擾動
            if (tNorm > TH) {
                targetNode = Collections.max(list1, Comparator.comparingInt(SurgeryNode::getSurgeryTime));
            } else { // 中低溫階段：移動手術時間最短的手術，進行小範圍微調
                targetNode = Collections.min(list1, Comparator.comparingInt(SurgeryNode::getSurgeryTime));
            }
            list1.remove(targetNode);

            // 50% 的機率進行交換 (swap)，50% 的機率進行插入 (insert)
            if (random.nextBoolean() && !list2.isEmpty()) {
                // 交換
                int indexToSwap = random.nextInt(list2.size());
                SurgeryNode nodeToSwap = list2.get(indexToSwap);
                list2.set(indexToSwap, targetNode);
                list1.add(nodeToSwap);
            } else {
                // 插入
                int insertIndex = list2.isEmpty() ? 0 : random.nextInt(list2.size() + 1);
                list2.add(insertIndex, targetNode);
            }

            // 檢查擾動後的解是否依然有效，如果有效就回傳
            if (isValid(neighbor)) {
                return neighbor;
            }
            tries++;
        }

        // 如果嘗試多次都無法產生有效解，則回傳原始解
        return currentSchedule;
    }

    /**
     * 根據問題規模計算自適應的冷卻係數
     * 
     * @param n 問題規模 (手術數量 * 房間數量)
     * @return 冷卻係數
     */
    private double calculateCoolingRate(int n) {
        return (ALPHA * Math.sqrt(n) - 1) / (ALPHA * Math.sqrt(n));
    }

    /**
     * 將最終的最佳排程結果更新回 DataManager
     * 
     * @param finalSchedule 最終的最佳排程
     */
    private void updateDataManagerWithSchedule(Schedule finalSchedule) {
        List<String[]> newTimetableData = new ArrayList<>();
        List<String[]> originalTimetable = dataManager.getTimetableData();

        // 保留原始 CSV 的表頭
        if (!originalTimetable.isEmpty()) {
            newTimetableData.add(originalTimetable.get(0));
        }

        Map<String, LinkedList<SurgeryNode>> roomSchedules = finalSchedule.getRoomSchedules();

        // 建立一個 Map 方便透過手術申請單號查找原始資料
        Map<String, String[]> originalDataMap = new HashMap<>();
        for (int i = 1; i < originalTimetable.size(); i++) {
            String[] row = originalTimetable.get(i);
            if (row.length > 1) {
                originalDataMap.put(row[1], row);
            }
        }

        // 根據最佳排程的結果，重新建立新的 Timetable 資料
        for (Map.Entry<String, LinkedList<SurgeryNode>> entry : roomSchedules.entrySet()) {
            String roomName = entry.getKey();
            List<SurgeryNode> surgeries = entry.getValue();

            for (SurgeryNode surgery : surgeries) {
                String[] originalRow = originalDataMap.get(surgery.getApplicationId());
                if (originalRow != null) {
                    String[] newRow = Arrays.copyOf(originalRow, originalRow.length);
                    newRow[5] = roomName; // 更新手術房欄位
                    newTimetableData.add(newRow);
                }
            }
        }
        dataManager.updateTimetableData(newTimetableData);
    }
}