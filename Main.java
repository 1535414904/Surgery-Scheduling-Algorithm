import java.io.IOException;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. 初始化並讀取所有資料
            DataManager dataManager = new DataManager();
            dataManager.readRoomData("in/room.csv");
            dataManager.readTimeTableData("in/TimeTable.csv");
            dataManager.readArgumentsData("in/Arguments4Exec.csv");

            // 2. 建立排程器並執行
            Scheduler scheduler = new Scheduler(dataManager);
            Map<String, Schedule> results = scheduler.schedule();

            // 如果排程失敗或沒有資料，則提前結束
            if (results.isEmpty()) {
                return;
            }

            // 3. 取得初始解與最佳解
            Schedule initialSchedule = results.get("initial");
            Schedule bestSchedule = results.get("best");

            // 4. 計算初始與最佳解的統計指標
            ScheduleMetrics initialMetrics = new ScheduleMetrics(initialSchedule, dataManager);
            ScheduleMetrics bestMetrics = new ScheduleMetrics(bestSchedule, dataManager);

            // 5. 在 Main 中印出詳細的比較報告
            printComparisonReport(initialMetrics, bestMetrics, dataManager);

            // 6. 寫入最終排程結果檔案
            dataManager.writeOutput("out/OutTimeTable.csv");

        } catch (IOException e) {
            System.err.println("發生錯誤：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // 專門用於印出比較報告的方法
    public static void printComparisonReport(ScheduleMetrics initial, ScheduleMetrics best, DataManager dataManager) {
        int roomCount = dataManager.getAllRooms().size();
        int maxRegularTime = dataManager.getArguments().getOrDefault("maxRegularTime", 540);
        long totalPossibleRegularTime = (long) roomCount * maxRegularTime;

        // 計算改善百分比的輔助函式
        String improvementPercentage = "↓%.2f%%";
        String increasePercentage = "↑%.2f%%";
        String format = "%-20s | %-16s | %-16s | %-16s\n";

        System.out.println("=========================================================================");
        System.out.println("                          手術排程最佳化結果報告");
        System.out.println("=========================================================================");
        System.out.printf(format, "指標", "初始排程 (Initial)", "最佳排程 (Best)", "改善率 (Reduction)");
        System.out.println("-------------------------------------------------------------------------");

        // 總成本
        double costReduction = initial.cost > 0 ? (1 - best.cost / initial.cost) * 100 : 0;
        System.out.printf(format, "總成本 (Cost)", String.format("%.2f", initial.cost),
                String.format("%.2f", best.cost),
                String.format(costReduction >= 0 ? improvementPercentage : increasePercentage,
                        Math.abs(costReduction)));

        // 總手術時間 (固定)
        System.out.printf(format, "總手術時間 (分鐘)", initial.totalSurgeryTime, best.totalSurgeryTime, "N/A");

        // 總使用時間
        double usageReduction = initial.totalUsageTime > 0
                ? (1 - (double) best.totalUsageTime / initial.totalUsageTime) * 100
                : 0;
        System.out.printf(format, "總使用時間 (分鐘)", initial.totalUsageTime, best.totalUsageTime,
                String.format(usageReduction >= 0 ? improvementPercentage : increasePercentage,
                        Math.abs(usageReduction)));

        System.out.println("-------------------------------------------------------------------------");

        // 總加班時間
        double regularOvertimeReduction = initial.totalRegularOvertime > 0
                ? (1 - (double) best.totalRegularOvertime / initial.totalRegularOvertime) * 100
                : 0;
        System.out.printf(format, "總加班時間 (分鐘)", initial.totalRegularOvertime, best.totalRegularOvertime,
                String.format(regularOvertimeReduction >= 0 ? improvementPercentage : increasePercentage,
                        Math.abs(regularOvertimeReduction)));

        // 總超時時間
        double overtimeReduction = initial.totalOvertime > 0
                ? (1 - (double) best.totalOvertime / initial.totalOvertime) * 100
                : 0;
        System.out.printf(format, "總超時時間 (分鐘)", initial.totalOvertime, best.totalOvertime,
                String.format(overtimeReduction >= 0 ? improvementPercentage : increasePercentage,
                        Math.abs(overtimeReduction)));

        System.out.println("-------------------------------------------------------------------------");

        // 加班率與超時率
        double initialRegularRate = totalPossibleRegularTime > 0
                ? (double) initial.totalRegularOvertime / totalPossibleRegularTime * 100
                : 0;
        double bestRegularRate = totalPossibleRegularTime > 0
                ? (double) best.totalRegularOvertime / totalPossibleRegularTime * 100
                : 0;
        double initialOvertimeRate = totalPossibleRegularTime > 0
                ? (double) initial.totalOvertime / totalPossibleRegularTime * 100
                : 0;
        double bestOvertimeRate = totalPossibleRegularTime > 0
                ? (double) best.totalOvertime / totalPossibleRegularTime * 100
                : 0;

        System.out.printf(format, "加班率", String.format("%.2f%%", initialRegularRate),
                String.format("%.2f%%", bestRegularRate), "N/A");
        System.out.printf(format, "超時率", String.format("%.2f%%", initialOvertimeRate),
                String.format("%.2f%%", bestOvertimeRate), "N/A");

        System.out.println("=========================================================================");

        System.out.printf("特殊手術房限制: %s\n", best.isSpecialRoomRequirementMet ? "✅ 符合" : "❌ 違反");

        System.out.println("\n詳細手術房使用情況 (排程後):");
        System.out.println("-------------------------------------------------------");

        for (Map.Entry<String, ScheduleMetrics.RoomMetrics> entry : best.roomMetrics.entrySet()) {
            ScheduleMetrics.RoomMetrics metrics = entry.getValue();
            System.out.printf("手術房 %-5s | 總使用: %4d 分 | 加班: %4d 分 | 超時: %4d 分\n",
                    entry.getKey(), metrics.usageTime, metrics.regularOvertime, metrics.overtime);
        }
        System.out.println("=======================================================");
    }
}