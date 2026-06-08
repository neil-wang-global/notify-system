<script setup>
import { ref, onMounted } from 'vue';
import { listUserOperationExceptions, listNotificationExceptions } from '../api/exceptions.js';

const activeTab = ref('user');
const userExceptions = ref([]);
const notificationExceptions = ref([]);
const loading = ref(true);
const initialLoadDone = ref(false);
const error = ref(null);
const expandedIds = ref(new Set());

function toggleExpand(id) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id);
  } else {
    expandedIds.value.add(id);
  }
}

function statusColor(status) {
  if (!status) return 'bg-slate-100 text-slate-600';
  switch (status.toUpperCase()) {
    case 'DEAD': return 'bg-red-100 text-red-700';
    case 'PENDING': return 'bg-amber-100 text-amber-700';
    case 'RETRYING': return 'bg-blue-100 text-blue-700';
    case 'RESOLVED': return 'bg-emerald-100 text-emerald-700';
    default: return 'bg-slate-100 text-slate-600';
  }
}

function statusLabel(status) {
  if (!status) return '-';
  switch (status.toUpperCase()) {
    case 'DEAD': return '已死亡';
    case 'PENDING': return '待处理';
    case 'RETRYING': return '重试中';
    case 'RESOLVED': return '已解决';
    default: return status;
  }
}

async function fetchExceptions() {
  loading.value = true;
  error.value = null;
  try {
    const [ue, ne] = await Promise.all([
      listUserOperationExceptions(),
      listNotificationExceptions(),
    ]);
    userExceptions.value = ue;
    notificationExceptions.value = ne;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
    initialLoadDone.value = true;
  }
}

onMounted(fetchExceptions);
</script>

<template>
  <div class="space-y-3 text-sm">
    <div class="flex items-center gap-3">
      <button
        @click="activeTab = 'user'"
        :class="['rounded-lg px-3 py-1 text-xs', activeTab === 'user' ? 'bg-blue-600 text-white' : 'bg-slate-100']"
      >用户操作异常</button>
      <button
        @click="activeTab = 'notification'"
        :class="['rounded-lg px-3 py-1 text-xs', activeTab === 'notification' ? 'bg-blue-600 text-white' : 'bg-slate-100']"
      >通知异常</button>
      <button @click="fetchExceptions" :disabled="loading" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200 disabled:opacity-50">
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

    <!-- User Operation Exceptions -->
    <div v-else-if="activeTab === 'user'">
      <div v-if="userExceptions.length === 0 && !loading" class="text-slate-400">暂无用户操作异常。</div>
      <div v-else>
        <div class="grid grid-cols-7 gap-2 rounded-lg bg-amber-50 p-3 font-medium text-amber-800">
          <span>ID</span><span>事件ID</span><span>客户</span><span>事件类型</span><span>失败原因</span><span>重试次数</span><span>状态</span>
        </div>
        <div>
          <div
            v-for="ex in userExceptions"
            :key="ex.id"
            class="border border-amber-200 rounded-lg mt-1"
          >
            <div
              class="grid grid-cols-7 gap-2 bg-amber-50 p-3 cursor-pointer hover:bg-amber-100 transition-colors"
              @click="toggleExpand('u-' + ex.id)"
            >
              <span class="truncate">{{ ex.id }}</span>
              <span class="truncate">{{ ex.eventId }}</span>
              <span class="truncate">{{ ex.customerId }}</span>
              <span>{{ ex.eventType }}</span>
              <span class="truncate">{{ ex.failureReason }}</span>
              <span>{{ ex.retryCount }}</span>
              <span class="flex items-center gap-1">
                <span :class="['rounded px-1.5 py-0.5 text-xs font-medium', statusColor(ex.status)]">
                  {{ statusLabel(ex.status) }}
                </span>
                <span class="text-slate-400">{{ expandedIds.has('u-' + ex.id) ? '▲' : '▼' }}</span>
              </span>
            </div>
            <!-- Expanded detail -->
            <div v-if="expandedIds.has('u-' + ex.id)" class="border-t border-amber-200 bg-amber-50/50 p-3">
              <pre class="whitespace-pre-wrap text-xs text-slate-600">{{ JSON.stringify(ex, null, 2) }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Notification Exceptions -->
    <div v-if="activeTab === 'notification'">
      <div v-if="notificationExceptions.length === 0 && !loading" class="text-slate-400">暂无通知异常。</div>
      <div v-else>
        <div class="grid grid-cols-7 gap-2 rounded-lg bg-rose-50 p-3 font-medium text-rose-800">
          <span>ID</span><span>通知ID</span><span>策略</span><span>客户</span><span>失败原因</span><span>重试次数</span><span>状态</span>
        </div>
        <div>
          <div
            v-for="ex in notificationExceptions"
            :key="ex.id"
            class="border border-rose-200 rounded-lg mt-1"
          >
            <div
              class="grid grid-cols-7 gap-2 bg-rose-50 p-3 cursor-pointer hover:bg-rose-100 transition-colors"
              @click="toggleExpand('n-' + ex.id)"
            >
              <span class="truncate">{{ ex.id }}</span>
              <span class="truncate">{{ ex.notificationId }}</span>
              <span class="truncate">{{ ex.strategyId }}</span>
              <span class="truncate">{{ ex.customerId }}</span>
              <span class="truncate">{{ ex.failureReason }}</span>
              <span>{{ ex.retryCount }}</span>
              <span class="flex items-center gap-1">
                <span :class="['rounded px-1.5 py-0.5 text-xs font-medium', statusColor(ex.status)]">
                  {{ statusLabel(ex.status) }}
                </span>
                <span class="text-slate-400">{{ expandedIds.has('n-' + ex.id) ? '▲' : '▼' }}</span>
              </span>
            </div>
            <!-- Expanded detail -->
            <div v-if="expandedIds.has('n-' + ex.id)" class="border-t border-rose-200 bg-rose-50/50 p-3">
              <pre class="whitespace-pre-wrap text-xs text-slate-600">{{ JSON.stringify(ex, null, 2) }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
