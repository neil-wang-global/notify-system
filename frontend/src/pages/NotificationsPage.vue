<script setup>
import { ref, onMounted } from 'vue';
import { listNotifications } from '../api/notifications.js';

const notifications = ref([]);
const loading = ref(false);
const error = ref(null);

async function fetchNotifications() {
  loading.value = true;
  error.value = null;
  try {
    notifications.value = await listNotifications();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(fetchNotifications);
</script>

<template>
  <div class="space-y-3 text-sm">
    <div class="flex items-center justify-between">
      <span class="font-medium text-slate-600">Notifications</span>
      <button @click="fetchNotifications" :disabled="loading" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200 disabled:opacity-50">
        {{ loading ? 'Loading...' : 'Refresh' }}
      </button>
    </div>

    <div v-if="error" class="rounded-lg bg-rose-50 p-3 text-rose-600">Error: {{ error }}</div>

    <div v-if="notifications.length === 0 && !loading" class="text-slate-400">
      No notifications found.
    </div>

    <div v-if="notifications.length > 0">
      <div class="grid grid-cols-6 gap-2 rounded-lg bg-slate-50 p-3 font-medium text-slate-600">
        <span>Notification ID</span><span>Strategy</span><span>Customer</span><span>Event Type</span><span>Triggered At</span><span>Current / Threshold</span>
      </div>
      <div
        v-for="n in notifications"
        :key="n.notificationId"
        class="grid grid-cols-6 gap-2 rounded-lg border border-slate-100 p-3"
      >
        <span class="truncate">{{ n.notificationId }}</span>
        <span class="truncate">{{ n.strategyId }}</span>
        <span class="truncate">{{ n.customerId }}</span>
        <span>{{ n.eventType }}</span>
        <span class="truncate">{{ n.triggeredAt }}</span>
        <span>{{ n.currentCount }} / {{ n.threshold }}</span>
      </div>
    </div>
  </div>
</template>
