import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class DataManager {
    private final Set<String> allRooms = new TreeSet<>();
    private final Set<String> specialRooms = new TreeSet<>();
    private final List<String[]> timetableData = new ArrayList<>(); // 原始 TimeTable 資料
    private final List<SurgeryNode> surgeryNodes = new ArrayList<>();
    private final Map<String, Integer> arguments = new HashMap<>();

    public void readRoomData(String filePath) throws IOException {
        // 使用 InputStreamReader 並指定 UTF-8 編碼
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            boolean isAllRoomsLine = false;
            boolean isSpecialRoomsLine = false;

            while ((line = reader.readLine()) != null) {
                // 檢查並移除 BOM
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.trim().isEmpty())
                    continue;

                if (line.startsWith("#")) {
                    isAllRoomsLine = line.contains("roomNamesOfAll");
                    isSpecialRoomsLine = line.contains("roomNames4Orth");
                    continue;
                }

                String[] rooms = line.replace("\"", "").split(",");
                if (isAllRoomsLine) {
                    Collections.addAll(allRooms, rooms);
                    isAllRoomsLine = false;
                } else if (isSpecialRoomsLine) {
                    Collections.addAll(specialRooms, rooms);
                    isSpecialRoomsLine = false;
                }
            }
        }
    }

    public void readTimeTableData(String filePath) throws IOException {
        // 使用 InputStreamReader 並指定 UTF-8 編碼
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                // 檢查並移除 BOM
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.trim().isEmpty())
                    continue;

                String[] data = line.split(",");
                // 將表頭和資料分開處理
                if (isHeader) {
                    timetableData.add(data);
                    isHeader = false;
                } else {
                    timetableData.add(data);
                    if (data.length >= 9) {
                        try {
                            SurgeryNode node = new SurgeryNode(data[1], data[5], Integer.parseInt(data[7]), data[8]);
                            surgeryNodes.add(node);
                        } catch (NumberFormatException e) {
                            System.err.println("警告：在 TimeTable.csv 中發現無效的數字格式，已跳過此行：" + line);
                        }
                    }
                }
            }
        }
    }

    public void readArgumentsData(String filePath) throws IOException {
        // 使用 InputStreamReader 並指定 UTF-8 編碼
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // 檢查並移除 BOM
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                // 忽略註解行與空行
                if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
            if (lines.size() >= 4) {
                arguments.put("startTime", Integer.parseInt(lines.get(0).trim()));
                arguments.put("maxRegularTime", Integer.parseInt(lines.get(1).trim()));
                arguments.put("maxOvertime", Integer.parseInt(lines.get(2).trim()));
                arguments.put("transitionTime", Integer.parseInt(lines.get(3).trim()));
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

    public Map<String, Integer> getArguments() {
        return arguments;
    }

    public List<String[]> getTimetableData() {
        return timetableData;
    }

    public void updateTimetableData(List<String[]> newTimetable) {
        this.timetableData.clear();
        this.timetableData.addAll(newTimetable);
    }

    public void writeOutput(String baseFileName) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String outputFileName = baseFileName.replace(".csv", "_" + timestamp + ".csv");

        // 寫入檔案時也明確指定 UTF-8 編碼，避免輸出亂碼
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outputFileName), StandardCharsets.UTF_8))) {
            for (String[] row : timetableData) {
                writer.write(String.join(",", row) + "\n");
            }
        }
        System.out.println("輸出完成：" + outputFileName);
    }
}