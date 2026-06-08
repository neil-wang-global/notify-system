<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { getStatus } from '../api/status.js';

const status = ref(null);
const loading = ref(false);
const error = ref(null);
let timer = null;

async function fetchStatus() {
  loading.value = true;
  error.value = null;
  try {
    status.value = await getStatus();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchStatus();
  timer = setInterval(fetchStatus, 5000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});

function statusColor(value) {
  if (!value) return 'slate';
  const v = value.toUpperCase();
  if (v === 'RUNNING' || v === 'HEALTHY') return 'emerald';
  if (v === 'DISABLED') return 'slate';
  return 'rose';
}
</script>

<template>
  <div class="space-y-2 text-sm">
    <div class="flex items-center justify-between mb-2">
      <span class="font-medium text-slate-600">System Status</span>
      <span v-if="loading" class="text-xs text-slate-400">Refreshing...</span>
    </div>

    <div v-if="error" class="rounded-lg bg-rose-50 p-3 text-rose-600">Error: {{ error }}</div>

    <ul v-if="status" class="space-y-2">
      <li :class="['rounded-lg p-3', `bg-${statusColor(status.kafka)}-50`, `text-${statusColor(status.kafka)}-700`]">
        Kafka consumer: {{ status.kafka }}
      </li>
      <li :class="['rounded-lg p-3', `bg-${statusColor(status.redis)}-50`, `text-${statusColor(status.redis)}-700`]">
        Redis cluster: {{ status.redis }}
      </li>
      <li class="rounded-lg bg-slate-50 p-3 text-slate-700">
        Strategy cache version: {{ status.strategyCacheVersion }}
      </li>
      <li :class="['rounded-lg p-3', status.degradationStatus === 'NONE' ? 'bg-slate-50 text-slate-700' : 'bg-rose-50 text-rose-700']">
        Degradation status: {{ status.degradationStatus }}
      </li>
    </ul>
    <div v-else-if="!error" class="text-slate-400">Loading status...</div>
  </div>
</template>
