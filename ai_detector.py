import cv2
import subprocess
import threading
import time
import requests
from ultralytics import YOLO

# ── 配置 ──────────────────────────────────────────────────
STREAM_IN   = "rtsp://localhost:8554/belt_raw"
STREAM_OUT  = "rtsp://localhost:8554/belt_ai"
BACKEND_URL = "http://localhost:8080/api/detect/alert"
MODEL_PATH  = "yolov8n.pt"   # 先用通用模型，后续换成训练好的撕裂模型

# ── 初始化模型 ────────────────────────────────────────────
print("加载 YOLOv8 模型...")
model = YOLO(MODEL_PATH)
print("模型加载完成")

# ── 连接相机流 ────────────────────────────────────────────
print("连接 MediaMTX...")
cap = cv2.VideoCapture(STREAM_IN, cv2.CAP_FFMPEG)
cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

if not cap.isOpened():
    print("连接失败，请确认 mediamtx.exe 正在运行")
    exit()

ret, frame = cap.read()
if not ret:
    print("读帧失败")
    exit()

H, W = frame.shape[:2]
print(f"画面尺寸: {W}x{H}")

# ── 启动 FFmpeg 把处理后的帧推回 MediaMTX ────────────────
ffmpeg_cmd = [
    "ffmpeg", "-y",
    "-f", "rawvideo",
    "-vcodec", "rawvideo",
    "-pix_fmt", "bgr24",
    "-s", f"{W}x{H}",
    "-r", "25",
    "-i", "pipe:0",
    "-vcodec", "libx264",
    "-pix_fmt", "yuv420p",
    "-preset", "ultrafast",
    "-tune", "zerolatency",
    "-f", "rtsp",
    "-rtsp_transport", "tcp",
    STREAM_OUT
]
print("FFmpeg 命令:", " ".join(ffmpeg_cmd))
ffmpeg_proc = subprocess.Popen(
    ffmpeg_cmd,
    stdin=subprocess.PIPE,
    stderr=None
)

# ── 推送检测结果给 Spring Boot ────────────────────────────
def push_result(torn, confidence, bbox):
    def send():
        try:
            requests.post(BACKEND_URL, json={
                "torn":       torn,
                "confidence": round(confidence, 3),
                "bbox":       bbox,
                "timestamp":  int(time.time() * 1000)
            }, timeout=1)
        except Exception as e:
            print(f"推送失败: {e}")
    threading.Thread(target=send, daemon=True).start()

# ── 主循环 ────────────────────────────────────────────────
print("开始 AI 检测，按 Ctrl+C 退出")
frame_count = 0

while True:
    ret, frame = cap.read()
    if not ret:
        print("读帧失败，重连中...")
        cap = cv2.VideoCapture(STREAM_IN, cv2.CAP_FFMPEG)
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        time.sleep(1)
        continue

    frame_count += 1

    # 每 3 帧推理一次（约 8fps 推理，降低 CPU 占用）
    if frame_count % 3 == 0:
        results = model(frame, verbose=False, conf=0.5)

        torn       = False
        confidence = 0.0
        bbox       = None

        for box in results[0].boxes:
            conf = float(box.conf)
            if conf > confidence:
                confidence = conf
                bbox       = [round(v, 1) for v in box.xyxy[0].tolist()]
                torn       = True

        # 画检测框
        annotated = results[0].plot()

        # 左上角状态文字
        status_text = "TORN!" if torn else "NORMAL"
        color       = (0, 0, 255) if torn else (0, 255, 100)
        cv2.putText(annotated, status_text, (15, 40),
                    cv2.FONT_HERSHEY_SIMPLEX, 1.2, color, 2)
        cv2.putText(annotated, f"CONF: {confidence:.2f}", (15, 75),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)

        # 有检测结果推给后端
        if torn:
            push_result(torn, confidence, bbox)

        output_frame = annotated
    else:
        output_frame = frame

    # 推给 FFmpeg → MediaMTX → 前端 belt_ai 频道
    try:
        ffmpeg_proc.stdin.write(output_frame.tobytes())
    except BrokenPipeError:
        print("FFmpeg 管道断开，退出")
        break

cap.release()
ffmpeg_proc.stdin.close()
ffmpeg_proc.wait()