<script setup>
import { ref, onMounted } from 'vue';
import { listNotifications } from '../api/notifications.js';

const notifications = ref([]);
const loading = ref(true);
const error = ref(null);
const initialLoadDone = ref(false);
const expandedIds = ref(new Set());

function toggleExpand(id) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id);
  } else {
    expandedIds.value.add(id);
  }
}

async function fetchNotifications() {
  loading.value = true;
  error.value = null;
  try {
    notifications.value = await listNotifications();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
    initialLoadDone.value = true;
  }
}

onMounted(fetchNotifications);
</script>

<template>
  <div class="space-y-3 text-sm">
    <div class="flex items-center justify-between">
      <span class="font-medium text-slate-600">通知记录</span>
      <button @click="fetchNotifications" :disabled="loading" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200 disabled:opacity-50">
        {{ loading ? '加载中...' : '刷新' }}
      </button>
    </div>

    <div v-if="error" class="rounded-lg bg-rose-50 p-3 text-rose-600">错误: {{ error }}</div>

    <!-- Loading skeleton -->
    <div v-if="loading && !initialLoadDone" class="space-y-2">
      <div class="h-10 rounded-lg bg-slate-100 animate-pulse"></div>
      <div class="h-10 rounded-lg bg-slate-100 animate-pulse"></div>
      <div class="h-10 rounded-lg bg-slate-100 animate-pulse"></div>
    </div>

    <div v-else-if="notifications.length === 0 && !loading" class="text-slate-400">
      暂无通知记录。
    </div>

    <div v-if="notifications.length > 0">
      <div class="grid grid-cols-6 gap-2 rounded-lg bg-slate-50 p-3 font-medium text-slate-600">
        <span>通知ID</span><span>策略</span><span>客户</span><span>事件类型</span><span>触发时间</span><span>当前 / 阈值</span>
      </div>
      <div>
        <div
          v-for="n in notifications"
          :key="n.notificationId"
          class="border border-slate-100 rounded-lg mt-1"
        >
          <div
            class="grid grid-cols-6 gap-2 p-3 cursor-pointer hover:bg-slate-50 transition-colors"
            @click="toggleExpand(n.notificationId)"
          >
            <span class="truncate">{{ n.notificationId }}</span>
            <span class="truncate">{{ n.strategyId }}</span>
            <span class="truncate">{{ n.customerId }}</span>
            <span>{{ n.eventType }}</span>
            <span class="truncate">{{ n.triggeredAt }}</span>
            <span>{{ n.currentCount }} / {{ n.threshold }}
              <span class="ml-1 text-slate-400">{{ expandedIds.has(n.notificationId) ? '▲' : '▼' }}</span>
            </span>
          </div>
          <!-- Expanded detail -->
          <div v-if="expandedIds.has(n.notificationId)" class="border-t border-slate-100 bg-slate-50 p-3 space-y-1 text-xs text-slate-600">
            <div><strong>事件ID:</strong> {{ n.eventId || '-' }}</div>
            <div><strong>用户ID:</strong> {{ n.userId || '-' }}</div>
            <div><strong>窗口:</strong> {{ n.windowSize || '-' }}</div>
            <div><strong>去重键:</strong> {{ n.dedupeKey || n.dedupKey || '-' }}</div>
            <div><strong>策略ID:</strong> {{ n.strategyId || '-' }}</div>
            <div v-if="Object.keys(n).length > 0" class="mt-2 pt-2 border-t border-slate-200">
              <strong>完整数据:</strong>
              <pre class="mt-1 whitespace-pre-wrap text-xs text-slate-500">{{ JSON.stringify(n, null, 2) }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
