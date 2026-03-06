<template>
  <div class="app">
    <TitleBar @open-alarms="() => {}" />

    <div class="main-grid">
      <PointCloudPanel class="pc-panel" />

      <div class="right-col">
        <CameraPanel class="cam-panel" />
        <DetectSidebar class="sidebar" />
      </div>
    </div>

    <StatusBar :connected="wsConnected" />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import TitleBar from '@/components/TitleBar.vue'
import PointCloudPanel from '@/components/PointCloudPanel.vue'
import CameraPanel from '@/components/CameraPanel.vue'
import DetectSidebar from '@/components/DetectSidebar.vue'
import StatusBar from '@/components/StatusBar.vue'
import { useWebSocket } from '@/composables/useWebSocket'

const { connected: wsConnected, connect } = useWebSocket()
onMounted(connect)
</script>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.main-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  overflow: hidden;
  border-top: none;
}

.pc-panel {
  border-right: 1px solid var(--border);
}

.right-col {
  display: grid;
  grid-template-columns: 1fr 220px;
  overflow: hidden;
}

.cam-panel {
  border-right: 1px solid var(--border);
}
</style>
