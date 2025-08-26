import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            // 初始化資料管理器
            DataManager dataManager = new DataManager();

            // 1. 讀取資料
            dataManager.readRoomData("in/room.csv");
            dataManager.readTimeTableData("in/TimeTable.csv");

            // 2. 建立鏈結串列與節點
            Scheduler scheduler = new Scheduler(dataManager);

            // 3. 執行排程（演算法部分留空）
            scheduler.schedule();

            // 4. 輸出結果
            dataManager.writeOutput("out/newtimetable.csv");
        } catch (IOException e) {
            System.err.println("發生錯誤：" + e.getMessage());
        }
    }
}