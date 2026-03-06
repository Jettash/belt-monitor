package com.wjiamao.belt_monitor.detect;

import com.wjiamao.belt_monitor.websocket.MonitorWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/detect")
@CrossOrigin(origins = "*")
public class DetectController {

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/alert")
    public void receiveAlert(@RequestBody Map<String, Object> alert) {
        try {
            Map<String, Object> msg = new HashMap<>();
        msg.put("type", "detection");
        
        // 直接把 Python 推来的字段平铺进去，和现有格式一致
        msg.put("status", alert.get("torn").equals(true) ? "alarm" : "normal");
        msg.put("results", List.of(
            Map.of("id", "tear", "label", "纵向撕裂",
                   "conf", alert.get("confidence"),
                   "color", "var(--danger)")
        ));
            // 复用现有的 broadcast 方法推给所有前端
            MonitorWebSocketHandler.broadcast(mapper.writeValueAsString(msg));
            System.out.println("[检测结果] " + alert);
        } catch (Exception e) {
            System.err.println("广播失败: " + e.getMessage());
        }
    }
}