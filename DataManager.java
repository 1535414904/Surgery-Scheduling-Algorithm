import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class DataManager {
    private final Set<String> allRooms = new TreeSet<>();
    private final Set<String> specialRooms = new TreeSet<>();
    private final List<String[]> timetableData = new ArrayList<>(); // 原始 TimeTable 資料
    private final List<SurgeryNode> surgeryNodes = new ArrayList<>();

    public void readRoomData(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#"))
                    continue; // 跳過註解行

                // 去掉引號並分隔房間名稱
                String[] rooms = line.replace("\"", "").split(",");
                if (line.contains("roomNamesOfAll")) {
                    Collections.addAll(allRooms, rooms);
                } else if (line.contains("roomNames4Orth")) {
                    Collections.addAll(specialRooms, rooms);
                }
            }
        }
    }

    public void readTimeTableData(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if (isHeader) {
                    timetableData.add(data); // 儲存表頭
                    isHeader = false;
                } else {
                    timetableData.add(data); // 儲存表內資料
                    if (data.length >= 9) {
                        SurgeryNode node = new SurgeryNode(data[1], data[5], Integer.parseInt(data[7]), data[8]);
                        surgeryNodes.add(node);
                    }
                }
            }
        }
    }

    public List<SurgeryNode> getSurgeryNodes() {
        return surgeryNodes;
    }

    public Set<String> getAllRooms() {
        return allRooms;
    }

    public Set<String> getSpecialRooms() {
        return specialRooms;
    }

    public void writeOutput(String baseFileName) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String outputFileName = baseFileName.replace(".csv", "_" + timestamp + ".csv");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName))) {
            for (String[] row : timetableData) {
                writer.write(String.join(",", row) + "\n");
            }
        }
        System.out.println("輸出完成：" + outputFileName);
    }
}