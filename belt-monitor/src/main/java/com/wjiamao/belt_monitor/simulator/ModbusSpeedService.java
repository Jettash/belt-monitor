package com.wjiamao.belt_monitor.simulator;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.util.SerialParameters;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * HGL-150 皮带测速仪 Modbus RTU 读取服务
 *
 * 仪器参数：COM4, 19200 baud, 8N1
 * 寄存器：从地址 1 读取 2 个保持寄存器（FC=03），解析为 IEEE 754 大端 float
 *
 * 当 modbus.enabled=false 或串口不可用时，返回 NaN，上层自动降级为模拟数据。
 */
@Slf4j
@Service
public class ModbusSpeedService {

    @Value("${modbus.enabled:true}")
    private boolean enabled;

    @Value("${modbus.port:COM4}")
    private String port;

    @Value("${modbus.baudrate:19200}")
    private int baudrate;

    @Value("${modbus.slave-id:1}")
    private int slaveId;

    @Value("${modbus.register:1}")
    private int register;

    private ModbusSerialMaster master;
    private volatile boolean connected = false;
    private volatile double  lastSpeed = Double.NaN;

    /** 上次尝试重连的时间戳，避免频繁重试 */
    private long lastReconnectAttemptMs = 0;
    private static final long RECONNECT_INTERVAL_MS = 5_000;

    @PostConstruct
    public void init() {
        if (enabled) {
            connect();
        }
    }

    private synchronized void connect() {
        try {
            if (master != null) {
                try { master.disconnect(); } catch (Exception ignored) {}
            }
            SerialParameters params = new SerialParameters();
            params.setPortName(port);
            params.setBaudRate(baudrate);
            params.setDatabits(8);
            params.setParity("None");
            params.setStopbits(1);
            params.setEncoding(Modbus.SERIAL_ENCODING_RTU);
            params.setEcho(false);

            master = new ModbusSerialMaster(params);
            master.connect();
            connected = true;
            log.info("Modbus 已连接: {} @ {} baud (slave={})", port, baudrate, slaveId);
        } catch (Exception e) {
            connected = false;
            log.warn("Modbus 连接失败 ({}): {}", port, e.getMessage());
        }
    }

    /**
     * 读取皮带速度 (m/s)。
     * 若未连接或读取异常，返回上次有效值（首次失败返回 NaN）。
     */
    public double readSpeed() {
        if (!enabled) {
            return Double.NaN;
        }

        if (!connected) {
            long now = System.currentTimeMillis();
            if (now - lastReconnectAttemptMs > RECONNECT_INTERVAL_MS) {
                lastReconnectAttemptMs = now;
                connect();
            }
            return lastSpeed;
        }

        try {
            Register[] regs = master.readMultipleRegisters(slaveId, register, 2);
            // 两个 16 位寄存器 → IEEE 754 大端 float
            int rawHigh = regs[0].getValue();
            int rawLow  = regs[1].getValue();
            float speed = Float.intBitsToFloat((rawHigh << 16) | (rawLow & 0xFFFF));
            lastSpeed = speed;
            return speed;
        } catch (Exception e) {
            log.warn("速度读取异常: {}", e.getMessage());
            connected = false;
            return lastSpeed;
        }
    }

    public boolean isConnected() {
        return enabled && connected;
    }

    @PreDestroy
    public void destroy() {
        if (master != null) {
            try { master.disconnect(); } catch (Exception ignored) {}
        }
    }
}
