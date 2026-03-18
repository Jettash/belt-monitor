<template>
  <div class="statusbar">
    <div v-for="s in sensors" :key="s.key" class="sb-item">
      <div class="sb-led" :class="s.on ? 'on' : 'off'"></div>
      <span>{{ s.label }}</span>
    </div>
    <div class="sb-sep"></div>
    <div class="sb-item">
      <div class="sb-led" :class="connected ? 'on' : 'off'"></div>
      <span>{{ connected ? 'WEBSOCKET 已连接' : 'WEBSOCKET 断开' }}</span>
    </div>
    <div class="sb-right">v1.0 · 北方魏家峁煤电有限责任公司 · 运煤皮带智能监测系统</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useMonitorStore } from '@/stores/monitor'

defineProps({ connected: Boolean })

const store = useMonitorStore()
const sensors = computed(() => [
  { key: 'lidar',  label: '三维雷达',   on: store.sensors.lidar  },
  { key: 'camera', label: '底部相机',   on: store.sensors.camera },
  { key: 'plc',    label: 'PLC 控制器', on: store.sensors.plc    },
  { key: 'server', label: '边缘服务器', on: store.sensors.server },
])
</script>

<style scoped>
.statusbar {
  height: 32px;
  flex-shrink: 0;
  background: var(--bg-deep, #060c14);
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  padding: 0 18px;
  gap: 0;
}
.sb-item {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  border-right: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}
.sb-led {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.sb-led.on  { background: var(--accent2); }
.sb-led.off { background: var(--danger); animation: pulse 1s ease-in-out infinite; }

.sb-sep { width: 1px; height: 16px; background: var(--border); margin: 0 4px; }

.sb-right {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}
</style>
