import cv2

cap = cv2.VideoCapture("rtsp://localhost:8554/belt_raw", cv2.CAP_FFMPEG)
cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

if not cap.isOpened():
    print("连接失败")
    exit()

print("连接成功")
while True:
    ret, frame = cap.read()
    if not ret:
        break
    cv2.imshow("test", frame)
    if cv2.waitKey(1) == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()