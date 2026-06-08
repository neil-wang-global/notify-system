<script setup>
import { ref, onMounted } from 'vue';
import { listUserOperationExceptions, listNotificationExceptions } from '../api/exceptions.js';

const activeTab = ref('user');
const userExceptions = ref([]);
const notificationExceptions = ref([]);
const loading = ref(false);
const error = ref(null);

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
      >User Operation Exceptions</button>
      <button
        @click="activeTab = 'notification'"
        :class="['rounded-lg px-3 py-1 text-xs', activeTab === 'notification' ? 'bg-blue-600 text-white' : 'bg-slate-100']"
      >Notification Exceptions</button>
      <button @click="fetchExceptions" :disabled="loading" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200 disabled:opacity-50">
        {{ loading ? 'Loading...' : 'Refresh' }}
      </button>
    </div>

    <div v-if="error" class="rounded-lg bg-rose-50 p-3 text-rose-600">Error: {{ error }}</div>

    <!-- User Operation Exceptions -->
    <div v-if="activeTab === 'user'">
      <div v-if="userExceptions.length === 0" class="text-slate-400">No user operation exceptions.</div>
      <div v-else>
        <div class="grid grid-cols-7 gap-2 rounded-lg bg-amber-50 p-3 font-medium text-amber-800">
          <span>ID</span><span>Event ID</span><span>Customer</span><span>Event Type</span><span>Failure Reason</span><span>Retry</span><span>Status</span>
        </div>
        <div
          v-for="ex in userExceptions"
          :key="ex.id"
          class="grid grid-cols-7 gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3"
        >
          <span class="truncate">{{ ex.id }}</span>
          <span class="truncate">{{ ex.eventId }}</span>
          <span class="truncate">{{ ex.customerId }}</span>
          <span>{{ ex.eventType }}</span>
          <span>{{ ex.failureReason }}</span>
          <span>{{ ex.retryCount }}</span>
          <span>{{ ex.status }}</span>
        </div>
      </div>
    </div>

    <!-- Notification Exceptions -->
    <div v-if="activeTab === 'notification'">
      <div v-if="notificationExceptions.length === 0" class="text-slate-400">No notification exceptions.</div>
      <div v-else>
        <div class="grid grid-cols-7 gap-2 rounded-lg bg-rose-50 p-3 font-medium text-rose-800">
          <span>ID</span><span>Notification ID</span><span>Strategy</span><span>Customer</span><span>Failure Reason</span><span>Retry</span><span>Status</span>
        </div>
        <div
          v-for="ex in notificationExceptions"
          :key="ex.id"
          class="grid grid-cols-7 gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3"
        >
          <span class="truncate">{{ ex.id }}</span>
          <span class="truncate">{{ ex.notificationId }}</span>
          <span class="truncate">{{ ex.strategyId }}</span>
          <span class="truncate">{{ ex.customerId }}</span>
          <span>{{ ex.failureReason }}</span>
          <span>{{ ex.retryCount }}</span>
          <span>{{ ex.status }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
