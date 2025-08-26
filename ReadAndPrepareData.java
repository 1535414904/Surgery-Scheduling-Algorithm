import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ReadAndPrepareData {

    public static void main(String[] args) {
        // 設定檔案路徑
        String timetablePath = "in\\TimeTable.csv";
        String roomCsvPath = "in\\room.csv";
        String argumentsPath = "in\\Arguments4Exec.csv";

        try {
            // 讀取 TimeTable.csv
            List<SurgeryNode> surgeryNodes = new ArrayList<>();
            Set<String> allRooms = new TreeSet<>(); // 所有手術房（無重複，自動排序）
            Set<String> specialRooms = new TreeSet<>(); // 特殊手術房（無重複，自動排序）
            readTimeTable(timetablePath, surgeryNodes, allRooms, specialRooms);

            // 輸出手術節點
            System.out.println("手術節點：");
            surgeryNodes.forEach(System.out::println);

            // 建立 room.csv
            createRoomCsv(roomCsvPath, allRooms, specialRooms);
            System.out.println("已建立 " + roomCsvPath);

            // 讀取 Arguments4Exec.csv
            Map<String, Integer> arguments = readArguments(argumentsPath);

            // 輸出排程參數
            System.out.println("排程參數：");
            arguments.forEach((key, value) -> System.out.println(key + "：" + value));
        } catch (IOException e) {
            System.err.println("處理檔案時發生錯誤：" + e.getMessage());
        }
    }

    private static void readTimeTable(String filePath, List<SurgeryNode> surgeryNodes, Set<String> allRooms,
            Set<String> specialRooms) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length >= 9) {
                    // 提取所需欄位
                    String applicationId = data[1];
                    String initialRoom = data[5];
                    int surgeryTime = Integer.parseInt(data[7]);
                    String specialRoomRequirement = data[8];

                    // 建立手術節點
                    SurgeryNode node = new SurgeryNode(applicationId, initialRoom, surgeryTime, specialRoomRequirement);
                    surgeryNodes.add(node);

                    // 加入到所有手術房集合
                    allRooms.add(initialRoom);

                    // 如果有特殊手術房需求，加入到特殊手術房集合
                    if ("Y".equalsIgnoreCase(specialRoomRequirement)) {
                        specialRooms.add(initialRoom);
                    }
                }
            }
        }
    }

    private static void createRoomCsv(String filePath, Set<String> allRooms, Set<String> specialRooms)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            writer.write("# roomNamesOfAll\n");
            writer.write("\"" + String.join(",", allRooms) + "\"\n");

            writer.write("# roomNames4Orth\n");
            writer.write("\"" + String.join(",", specialRooms) + "\"\n");
        }
    }

    private static Map<String, Integer> readArguments(String filePath) throws IOException {
        Map<String, Integer> arguments = new LinkedHashMap<>(); // 保留順序
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.startsWith("#")) {
                    continue; // 跳過註解行
                }
                switch (lineCount) {
                    case 2 -> arguments.put("每日開始排程時間", Integer.parseInt(line.trim()));
                    case 4 -> arguments.put("每日允許可用的最大常規期間", Integer.parseInt(line.trim()));
                    case 6 -> arguments.put("每日允許可用的最大超時期間", Integer.parseInt(line.trim()));
                    case 8 -> arguments.put("兩檯手術之間的銜接期間", Integer.parseInt(line.trim()));
                }
            }
        }
        return arguments;
    }

    // 手術節點類別
    static class SurgeryNode {
        private final String applicationId;
        private final String initialRoom;
        private final int surgeryTime;
        private final String specialRoomRequirement;

        public SurgeryNode(String applicationId, String initialRoom, int surgeryTime, String specialRoomRequirement) {
            this.applicationId = applicationId;
            this.initialRoom = initialRoom;
            this.surgeryTime = surgeryTime;
            this.specialRoomRequirement = specialRoomRequirement;
        }

        @Override
        public String toString() {
            return "SurgeryNode{" +
                    "申請序號='" + applicationId + '\'' +
                    ", 初始手術房='" + initialRoom + '\'' +
                    ", 手術時間=" + surgeryTime +
                    ", 特殊手術房需求='" + specialRoomRequirement + '\'' +
                    '}';
        }
    }
}