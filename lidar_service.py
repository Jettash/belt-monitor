"""
lidar_service.py  v3
SICK LMS 系列皮带截面采集服务

职责：连接雷达 → 解析帧 → 计算截面积/堆高 → 每秒推给 Java 后端
     载量计算、吨数累计、数据库存储全部交给 Java 处理

变更（相比 v2）：
  - 移除 BELT_SPEED / COAL_DENSITY / load 计算（Java 端做）
  - 移除 SQLite 存储（Java H2 统一存储）
  - 移除独立推送线程，改为采集循环内每秒推一次均值
  - ANGLE_MIN/MAX 改为 65/115（留余量覆盖皮带全宽）
  - 最小有效点数从 3 改为 10
  - 新增离群点过滤（coal_h > MAX_COAL_H_MM 的点丢弃）
  - 新增大间距跳过（相邻点水平距离 > 80mm 时不做梯形积分）

依赖：pip install requests
"""

import socket
import math
import json
import time
import threading
import logging
import requests
from datetime import datetime

# ── 日志 ─────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler("lidar_service.log", encoding="utf-8"),
        logging.StreamHandler()
    ]
)
log = logging.getLogger(__name__)

# ══════════════════════════════════════════════════════════════════
# 参数配置
# ══════════════════════════════════════════════════════════════════

LIDAR_IP   = "169.254.175.235"
LIDAR_PORT = 2111

INSTALL_HEIGHT = 1500.0   # mm  雷达安装高度（到空皮带面距离），现场确认后修改
BELT_WIDTH     = 1250.0   # mm  皮带有效宽度

# 有效扫描角度范围
# arctan(625/1500)=22.6°，90°±22.6° → 需要 67.4~112.6°
# 取 65~115 留出余量
ANGLE_MIN = 65.0
ANGLE_MAX = 115.0

X_OFFSET_MM = 0.0   # mm  雷达水平安装偏移（向右为正）
PITCH_DEG   = 0.0   # 度  雷达俯仰安装偏差（朝下为正）

EMPTY_THRESH_M2 = 0.002   # m²  低于此值视为空载，不推送非零面积
MAX_COAL_H_MM   = 600.0   # mm  超过此值视为离群点（约皮带槽最大深度）
MIN_POINTS      = 10      # 有效点数不足时丢弃该帧

BACKEND_URL    = "http://localhost:8080/api/lidar/frame"
PUSH_INTERVAL  = 1.0      # 秒，每 1 秒推一次（多帧均值）
RECONNECT_WAIT = 5        # 秒

# ══════════════════════════════════════════════════════════════════
# 基准线（空皮带轮廓）
# ══════════════════════════════════════════════════════════════════

# 在没有标定文件时，用抛物线近似皮带槽形：
#   边缘（x=±625mm）比中心低 250mm
_BELT_A        = 250.0 / (625.0 ** 2)
_baseline_poly = None


def baseline_h(x_mm):
    """返回 x 处的空皮带基准高度（mm），越高表示皮带越深"""
    if _baseline_poly is not None:
        try:
            return float(_baseline_poly(x_mm))
        except Exception:
            pass
    return _BELT_A * (x_mm ** 2)


def load_calibration():
    """尝试加载现场标定的基准线多项式，没有则使用抛物线"""
    global _baseline_poly
    try:
        import numpy as np
        with open("calibration.json") as f:
            c = json.load(f)
        _baseline_poly = np.poly1d(c["poly_coeffs"])
        log.info("已加载标定文件 (%s)" % c.get("timestamp", "?")[:19])
    except ImportError:
        log.warning("numpy 未安装，使用抛物线估算基准线（精度较低）")
    except FileNotFoundError:
        log.warning("未找到 calibration.json，使用抛物线估算基准线（精度较低）")
        log.warning("建议：空载运行时执行标定程序生成 calibration.json")


# ══════════════════════════════════════════════════════════════════
# 帧解析
# 索引来自 C# 原版验证（本台雷达实测）
#   tokens[23] 起始角度  (hex, 单位 1/10000°)
#   tokens[24] 步长角度  (hex, 单位 1/10000°)
#   tokens[25] 点数量    (hex)
#   tokens[26..] 距离值  (hex, 单位 mm)
# ══════════════════════════════════════════════════════════════════

def parse_frame(block):
    if not block.startswith("sSN LMDscandata"):
        return None

    tokens = block.split()
    if len(tokens) < 30:
        return None

    # 帧末尾状态位校验（C# 原版逻辑）
    try:
        if not (tokens[-1] == "0" and tokens[-3] == "0"
                and tokens[-5] == "0" and tokens[-7] == "0"):
            return None
    except IndexError:
        return None

    try:
        start_angle = int(tokens[23], 16) / 10000.0
        step        = int(tokens[24], 16) / 10000.0
        n           = int(tokens[25], 16)

        if n <= 0 or n > 10000:
            return None

        dists = [
            int(tokens[26 + i], 16)
            for i in range(n)
            if 26 + i < len(tokens)
        ]
        if len(dists) != n:
            return None

        return dists, start_angle, step

    except (ValueError, IndexError):
        return None


# ══════════════════════════════════════════════════════════════════
# 安装偏差修正
# ══════════════════════════════════════════════════════════════════

def apply_install_correction(x_raw, vert_raw):
    if PITCH_DEG == 0.0 and X_OFFSET_MM == 0.0:
        return x_raw, vert_raw
    pitch_rad      = math.radians(PITCH_DEG)
    vert_corrected = vert_raw * math.cos(pitch_rad) - x_raw * math.sin(pitch_rad)
    x_corrected    = x_raw   * math.cos(pitch_rad) + vert_raw * math.sin(pitch_rad)
    x_corrected   -= X_OFFSET_MM
    return x_corrected, vert_corrected


# ══════════════════════════════════════════════════════════════════
# 截面积计算（不再计算载量，由 Java 端处理）
# ══════════════════════════════════════════════════════════════════

def calc_section(dists, start_deg, step_deg):
    """
    返回 (area_m2, heap_height_m)
      area_m2      截面积，< EMPTY_THRESH_M2 时返回 0.0
      heap_height_m 最大堆高，area=0 时同为 0.0
    """
    pts = []   # [(x_mm, coal_h_mm), ...]

    for i, d in enumerate(dists):
        # 距离有效性过滤
        if not (50 < d < 8000):
            continue

        ang = start_deg + i * step_deg
        if not (ANGLE_MIN <= ang <= ANGLE_MAX):
            continue

        r        = math.radians(ang)
        x_raw    = -d * math.cos(r)
        vert_raw =  d * math.sin(r)
        x, vert  = apply_install_correction(x_raw, vert_raw)

        if abs(x) > BELT_WIDTH / 2:
            continue

        coal_h = (INSTALL_HEIGHT - vert) - baseline_h(x)

        # 离群点过滤：负值（低于基准线）或超过最大堆高
        if coal_h < 0 or coal_h > MAX_COAL_H_MM:
            continue

        pts.append((x, coal_h))

    if len(pts) < MIN_POINTS:
        return 0.0, 0.0

    pts.sort(key=lambda p: p[0])

    # 梯形积分，相邻点间距 > 80mm 时跳过（大间距区间误差大）
    area_mm2 = 0.0
    for i in range(len(pts) - 1):
        dx = pts[i + 1][0] - pts[i][0]
        if dx > 80:
            continue
        area_mm2 += (pts[i][1] + pts[i + 1][1]) / 2.0 * dx

    area_m2 = area_mm2 / 1e6

    if area_m2 < EMPTY_THRESH_M2:
        return 0.0, 0.0

    heap_height_m = max(h for _, h in pts) / 1000.0
    return round(area_m2, 5), round(heap_height_m, 4)


# ══════════════════════════════════════════════════════════════════
# 采集主循环
# ══════════════════════════════════════════════════════════════════

def collect_loop(stop_evt):
    while not stop_evt.is_set():
        sock = None

        # 帧缓冲（用于 1 秒内多帧均值）
        frame_areas   = []
        frame_heights = []
        last_push_ts  = time.time()
        last_log_ts   = time.time()

        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(5.0)
            sock.connect((LIDAR_IP, LIDAR_PORT))
            log.info("[雷达] 已连接 %s:%d" % (LIDAR_IP, LIDAR_PORT))
            sock.sendall(b"\x02sEN LMDscandata 1\x03")

            buf = b""

            while not stop_evt.is_set():
                try:
                    chunk = sock.recv(8192)
                    if not chunk:
                        raise ConnectionResetError("服务端关闭连接")
                    buf += chunk
                except socket.timeout:
                    continue

                # 从 buf 中逐帧提取
                while True:
                    s = buf.find(b"\x02")
                    e = buf.find(b"\x03")
                    if s == -1 or e == -1 or e < s:
                        break

                    block = buf[s + 1:e].decode("ascii", errors="ignore")
                    buf   = buf[e + 1:]

                    result = parse_frame(block)
                    if not result:
                        continue

                    dists, start, step = result
                    area, height       = calc_section(dists, start, step)

                    frame_areas.append(area)
                    frame_heights.append(height)

                # 每 PUSH_INTERVAL 秒推一次均值
                now = time.time()
                if now - last_push_ts >= PUSH_INTERVAL:
                    if frame_areas:
                        avg_area   = sum(frame_areas)   / len(frame_areas)
                        avg_height = sum(frame_heights) / len(frame_heights)
                        n_frames   = len(frame_areas)
                    else:
                        avg_area, avg_height, n_frames = 0.0, 0.0, 0

                    frame_areas.clear()
                    frame_heights.clear()
                    last_push_ts = now

                    try:
                        requests.post(BACKEND_URL, json={
                            "crossArea":  round(avg_area,   5),
                            "heapHeight": round(avg_height, 4),
                        }, timeout=1.0)
                    except Exception as e:
                        log.debug("[推送失败] %s" % e)

                    # 每 5 秒打一条日志
                    if now - last_log_ts >= 5:
                        log.info(
                            "[采集] 截面积=%.4fm²  堆高=%.3fm  帧数/秒=%d"
                            % (avg_area, avg_height, n_frames)
                        )
                        last_log_ts = now

        except Exception as e:
            log.warning("[雷达] 断开: %s，%ds 后重连..." % (e, RECONNECT_WAIT))
            if sock:
                try:
                    sock.sendall(b"\x02sEN LMDscandata 0\x03")
                    sock.close()
                except Exception:
                    pass
            stop_evt.wait(RECONNECT_WAIT)


# ══════════════════════════════════════════════════════════════════
# 入口
# ══════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    log.info("=" * 55)
    log.info("  皮带截面采集服务  v3  启动")
    log.info("=" * 55)
    log.info("  雷达地址:  %s:%d" % (LIDAR_IP, LIDAR_PORT))
    log.info("  安装高度:  %.0f mm" % INSTALL_HEIGHT)
    log.info("  皮带宽度:  %.0f mm" % BELT_WIDTH)
    log.info("  有效角度:  %.1f ~ %.1f 度" % (ANGLE_MIN, ANGLE_MAX))
    log.info("  空载阈值:  %.4f m²" % EMPTY_THRESH_M2)
    log.info("  离群过滤:  > %.0f mm 丢弃" % MAX_COAL_H_MM)
    log.info("  最小点数:  %d" % MIN_POINTS)
    log.info("  后端地址:  %s" % BACKEND_URL)
    log.info("=" * 55)

    load_calibration()

    stop_evt = threading.Event()
    try:
        collect_loop(stop_evt)
    except KeyboardInterrupt:
        log.info("收到停止信号 (Ctrl+C)")
        stop_evt.set()
        log.info("服务已停止")
