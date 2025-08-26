public class SurgeryNode {
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

    public String getApplicationId() {
        return applicationId;
    }

    public String getInitialRoom() {
        return initialRoom;
    }

    public int getSurgeryTime() {
        return surgeryTime;
    }

    public String getSpecialRoomRequirement() {
        return specialRoomRequirement;
    }

    public String toCsvString() {
        return applicationId + "," + initialRoom + "," + surgeryTime + "," + specialRoomRequirement;
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