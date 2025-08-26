import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            // 初始化資料管理器
            DataManager dataManager = new DataManager();

            // 1. 讀取所有輸入資料
            dataManager.readRoomData("in/room.csv");
            dataManager.readTimeTableData("in/TimeTable.csv");
            dataManager.readArgumentsData("in/Arguments4Exec.csv");

            // 2. 建立排程器
            Scheduler scheduler = new Scheduler(dataManager);

            // 3. 執行排程
            scheduler.schedule();

            // 4. 輸出結果
            dataManager.writeOutput("out/OutTimeTable.csv");
        } catch (IOException e) {
            System.err.println("發生錯誤：" + e.getMessage());
            e.printStackTrace();
        }
    }
}