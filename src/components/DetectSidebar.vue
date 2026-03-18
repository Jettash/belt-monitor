<template>
  <div class="sidebar">
    <div class="sb-block">
      <div class="sb-title">AI 检测结果</div>
      <div
        v-for="item in store.detection.results"
        :key="item.id"
        class="det-item"
      >
        <div class="det-row">
          <span class="det-name" :style="{ color: item.conf > 50 ? item.color : '' }">
            {{ item.label }}
          </span>
          <span class="det-conf">{{ item.conf.toFixed(1) }}%</span>
        </div>
        <div class="det-track">
          <div
            class="det-fill"
            :style="{
              width: item.conf + '%',
              background: item.color
            }"
          ></div>
        </div>
      </div>
      <div class="det-footer">
        <span>YOLOv8 · 推理耗时</span>
        <span class="det-ms">{{ store.metrics.inferMs }} ms</span>
      </div>
    </div>

    <div class="sb-block">
      <div class="sb-title">运行参数</div>
      <div class="param-row">
        <span class="pk">皮带速度</span>
        <span class="pv">{{ store.metrics.speed.toFixed(2) }}<em> m/s</em></span>
      </div>
      <div class="param-row">
        <span class="pk">今日累计</span>
        <span class="pv">{{ fmtT(store.metrics.dayTon) }}<em> t</em></span>
      </div>
      <div class="param-row">
        <span class="pk">运行时长</span>
        <span class="pv">{{ fmtRuntime }}</span>
      </div>
      <div class="param-row">
        <span class="pk">均值载量</span>
        <span class="pv">{{ fmtT(store.metrics.avgLoad) }}<em> t/h</em></span>
      </div>
    </div>

    <div class="sb-block event-block">
      <div class="sb-title">事件日志</div>
      <div class="event-row">
        <span class="event-time">09:30:00</span>
        <span class="event-msg">皮带启动</span>
      </div>
    </div>

    <!-- Commented out by request: hide warning/event log block in UI. -->
    <!--
    <div class="alarm-block">
      <div class="sb-title alarm-title">
        事件日志
        <button class="read-btn" @click="store.markAllRead()">全部已读</button>
      </div>
      <div class="alarm-scroll">
        <TransitionGroup name="alarm-list">
          <div
            v-for="alarm in store.alarms"
            :key="alarm.id"
            class="alarm-row"
            :class="['lvl-' + alarm.level, alarm.unread ? 'unread' : '']"
          >
            <span class="al-badge" :class="'badge-' + alarm.level">{{ alarm.label }}</span>
            <div class="al-body">
              <div class="al-msg">{{ alarm.msg }}</div>
              <div class="al-meta">{{ alarm.time }} · {{ alarm.src }}</div>
            </div>
          </div>
        </TransitionGroup>
      </div>
    </div>
    -->
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useMonitorStore } from '@/stores/monitor'

const store = useMonitorStore()

const fmtRuntime = computed(() => {
  const s = store.metrics.runtimeSec
  const h = String(Math.floor(s / 3600)).padStart(2, '0')
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${h}:${m}:${ss}`
})

function fmtT(v) {
  return Math.round(v).toLocaleString()
}
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-panel);
  border-left: 1px solid var(--border);
}

.sb-block {
  padding: 16px 18px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.sb-title {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.sb-title::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border);
}

.det-item {
  margin-bottom: 10px;
}
.det-item:last-child {
  margin-bottom: 0;
}
.det-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 4px;
}
.det-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-sec);
  transition: color 0.3s;
}
.det-conf {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-muted);
}
.det-track {
  height: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  overflow: hidden;
  border-radius: 1px;
}
.det-fill {
  height: 100%;
  transition: width 0.5s ease, background 0.3s;
  border-radius: 1px;
}
.det-footer {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}
.det-ms {
  color: var(--accent2);
}

.param-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 7px 0;
  border-bottom: 1px solid var(--border);
}
.param-row:last-child {
  border-bottom: none;
}
.pk {
  font-size: 13px;
  color: var(--text-sec);
}
.pv {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-pri);
}
.pv em {
  font-style: normal;
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 2px;
}

.event-block {
  border-bottom: none;
}
.event-row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 0;
  border: 1px solid var(--border);
  border-radius: 2px;
  background: var(--bg-card);
}
.event-time {
  min-width: 70px;
  padding-left: 10px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
}
.event-msg {
  font-size: 13px;
  color: var(--text-pri);
}

.alarm-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.alarm-title {
  padding: 12px 18px 8px;
  border-bottom: 1px solid var(--border);
  margin: 0;
  flex-shrink: 0;
}
.read-btn {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-muted);
  background: none;
  border: 1px solid var(--border);
  padding: 2px 8px;
  cursor: pointer;
  border-radius: 2px;
  letter-spacing: 0.04em;
}
.read-btn:hover {
  color: var(--text-pri);
  border-color: var(--border-glow);
}
.alarm-scroll {
  flex: 1;
  overflow-y: auto;
}

.alarm-row {
  padding: 10px 18px;
  border-bottom: 1px solid var(--border);
  display: flex;
  gap: 8px;
  align-items: flex-start;
  transition: background 0.1s;
  border-left: 2px solid transparent;
}
.alarm-row:hover {
  background: var(--bg-hover);
}
.alarm-row.lvl-danger.unread {
  border-left-color: var(--danger);
  background: rgba(255, 59, 92, 0.025);
}
.alarm-row.lvl-warn.unread {
  border-left-color: var(--warn);
}
.alarm-row.lvl-ok {
  border-left-color: transparent;
}

.al-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
  padding: 2px 7px;
  border: 1px solid;
  margin-top: 1px;
  white-space: nowrap;
  flex-shrink: 0;
  border-radius: 2px;
}
.badge-danger {
  color: var(--danger);
  border-color: rgba(255, 59, 92, 0.4);
  background: rgba(255, 59, 92, 0.08);
}
.badge-warn {
  color: var(--warn);
  border-color: rgba(255, 170, 0, 0.35);
  background: rgba(255, 170, 0, 0.06);
}
.badge-info {
  color: var(--accent);
  border-color: rgba(0, 212, 255, 0.3);
  background: rgba(0, 212, 255, 0.05);
}
.badge-ok {
  color: var(--accent2);
  border-color: rgba(0, 255, 157, 0.3);
  background: rgba(0, 255, 157, 0.05);
}

.al-body {
  flex: 1;
  min-width: 0;
}
.al-msg {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-pri);
  line-height: 1.4;
}
.al-meta {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.alarm-list-enter-active {
  animation: slideInRight 0.25s ease;
}
.alarm-list-leave-active {
  transition: opacity 0.2s;
}
.alarm-list-leave-to {
  opacity: 0;
}
</style>
