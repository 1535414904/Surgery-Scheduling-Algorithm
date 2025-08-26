import csv
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
from matplotlib import rcParams

# 設定字型為微軟正黑體
rcParams['font.sans-serif'] = ['Microsoft JhengHei']  # 設定中文字型
rcParams['axes.unicode_minus'] = False               # 解決負號顯示問題

def read_arguments(file_path):
    """讀取 Arguments4Exec.csv 參數"""
    arguments = {}
    with open(file_path, mode='r', encoding='utf-8') as file:
        lines = file.readlines()
        arguments['每日開始排程時間'] = int(lines[1].strip())
        arguments['每日允許可用的最大常規期間'] = int(lines[3].strip())
        arguments['每日允許可用的最大超時期間'] = int(lines[5].strip())
        arguments['兩檯手術之間的銜接期間'] = int(lines[7].strip())
    return arguments

def read_rooms(file_path):
    """讀取 room.csv 資料"""
    all_rooms = []
    special_rooms = set()
    with open(file_path, mode='r', encoding='utf-8') as file:
        lines = file.readlines()
        all_rooms = lines[1].strip().replace('"', '').split(',')
        special_rooms = set(lines[3].strip().replace('"', '').split(','))
    return all_rooms, special_rooms

def read_timetable(file_path):
    """讀取 TimeTable.csv 資料"""
    timetable = []
    with open(file_path, mode='r', encoding='utf-8') as file:
        csv_reader = csv.reader(file)
        for row in csv_reader:
            timetable.append(row)
    return timetable

def calculate_time_in_blocks(timetable, arguments, all_rooms):
    """計算手術在不同背景區塊的時間"""
    start_time = arguments['每日開始排程時間']
    max_regular = arguments['每日允許可用的最大常規期間']
    max_overtime = arguments['每日允許可用的最大超時期間']
    transition_time = arguments['兩檯手術之間的銜接期間']
    
    yellow_start = start_time + max_regular
    red_start = yellow_start + max_overtime
    
    time_in_yellow = 0
    time_in_red = 0

    room_current_time = {room: start_time for room in all_rooms}  # 每個房間的時間起點
    for surgery in timetable[1:]:  # 跳過標題行
        surgery_date, app_id, patient_id, dept, surgeon, room, anaesthesia, duration, special, priority = surgery
        duration = int(duration)
        surgery_start = room_current_time[room]
        surgery_end = surgery_start + duration

        # 計算手術在黃色區塊的時間
        if surgery_start < red_start and surgery_end > yellow_start:
            overlap_yellow_start = max(surgery_start, yellow_start)
            overlap_yellow_end = min(surgery_end, red_start)
            time_in_yellow += max(0, overlap_yellow_end - overlap_yellow_start)

        # 計算手術在紅色區塊的時間
        if surgery_start < 1440 and surgery_end > red_start:
            overlap_red_start = max(surgery_start, red_start)
            overlap_red_end = min(surgery_end, 1440)
            time_in_red += max(0, overlap_red_end - overlap_red_start)

        # 更新房間的時間起點
        room_current_time[room] = surgery_end + transition_time

    return time_in_yellow, time_in_red

def plot_gantt(timetable, all_rooms, special_rooms, arguments, ax, title):
    """繪製甘特圖"""
    room_indices = {room: i for i, room in enumerate(all_rooms)}  # 房間對應的索引
    start_time = arguments['每日開始排程時間']
    max_regular = arguments['每日允許可用的最大常規期間']
    max_overtime = arguments['每日允許可用的最大超時期間']
    transition_time = arguments['兩檯手術之間的銜接期間']

    # 計算最後一檯手術結束的時間
    room_current_time = {room: start_time for room in all_rooms}  # 每個房間的時間起點
    for surgery in timetable[1:]:  # 跳過標題行
        surgery_date, app_id, patient_id, dept, surgeon, room, anaesthesia, duration, special, priority = surgery
        duration = int(duration)
        surgery_start = room_current_time[room]
        surgery_end = surgery_start + duration + transition_time
        room_current_time[room] = surgery_end

    final_end_time = max(room_current_time.values())  # 所有房間的最後結束時間

    # 背景顏色覆蓋到最後一檯手術的結束時間
    ax.axvspan(start_time, start_time + max_regular, color='lightgreen', alpha=0.5, label='正常時間')
    ax.axvspan(start_time + max_regular, start_time + max_regular + max_overtime, color='khaki', alpha=0.5, label='加班時間')
    ax.axvspan(start_time + max_regular + max_overtime, final_end_time, color='lightcoral', alpha=0.5, label='超時時間')

    room_current_time = {room: start_time for room in all_rooms}  # 重置各房間的時間起點
    for surgery in timetable[1:]:
        surgery_date, app_id, patient_id, dept, surgeon, room, anaesthesia, duration, special, priority = surgery
        duration = int(duration)
        room_name = room + (' (Y)' if room in special_rooms else '')
        y_pos = room_indices[room]

        # 確保手術開始時間在每日開始排程時間之後
        surgery_start = room_current_time[room]
        surgery_end = surgery_start + duration

        # 畫手術區塊
        ax.barh(y_pos, duration, left=surgery_start, color='skyblue', edgecolor='black')
        # 顯示手術編號
        ax.text(surgery_start + duration / 2, y_pos, app_id, ha='center', va='center', fontsize=8, color='black')
        if special == 'Y':  # 特殊手術
            ax.add_patch(Rectangle((surgery_start, y_pos - 0.4), duration, 0.8, edgecolor='red', fill=False, lw=2))

        # 畫銜接時間
        ax.barh(y_pos, transition_time, left=surgery_end, color='blue', edgecolor='black')

        # 更新房間的時間起點
        room_current_time[room] = surgery_end + transition_time

    # 房間名稱
    ax.set_yticks(range(len(all_rooms)))
    ax.set_yticklabels(all_rooms)
    ax.set_xlim(start_time, final_end_time)  # 從開始時間到最後一檯手術的結束時間
    ax.set_xlabel('時間 (分鐘)')
    ax.set_title(title)
    ax.legend()

def main():
    # 讀取資料
    arguments = read_arguments('in/Arguments4Exec.csv')
    all_rooms, special_rooms = read_rooms('in/room.csv')
    original_timetable = read_timetable('in/TimeTable.csv')
    output_timetable = read_timetable('out/OutTimeTable.csv')

    # 終端機輸出統計結果
    original_yellow, original_red = calculate_time_in_blocks(original_timetable, arguments, all_rooms)
    output_yellow, output_red = calculate_time_in_blocks(output_timetable, arguments, all_rooms)
    print(f"排程前：黃色區塊 {original_yellow} 分鐘，紅色區塊 {original_red} 分鐘")
    print(f"排程後：黃色區塊 {output_yellow} 分鐘，紅色區塊 {output_red} 分鐘")

    # 繪製甘特圖
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(15, 10), sharex=True)
    plot_gantt(original_timetable, all_rooms, special_rooms, arguments, ax1, '原始手術排程')
    plot_gantt(output_timetable, all_rooms, special_rooms, arguments, ax2, '輸出手術排程')

    plt.tight_layout()
    plt.savefig('out/surgery_gantt_chart.png')
    plt.show()

if __name__ == '__main__':
    main()