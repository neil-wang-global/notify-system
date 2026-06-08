<script setup>
import { ref } from 'vue';
import { simulateEvent } from '../api/events.js';

const eventId = ref('');
const customerId = ref('');
const userId = ref('');
const eventType = ref('');
const userGroupIds = ref('');
const customFields = ref([{ key: '', value: '' }]);

const sending = ref(false);
const result = ref(null);
const error = ref(null);

function addField() {
  customFields.value.push({ key: '', value: '' });
}

function removeField(index) {
  customFields.value.splice(index, 1);
}

async function handleSend() {
  sending.value = true;
  error.value = null;
  result.value = null;

  try {
    const fields = {};
    customFields.value.forEach(f => {
      if (f.key.trim()) fields[f.key.trim()] = f.value;
    });

    const body = {
      eventId: eventId.value || undefined,
      customerId: customerId.value,
      userId: userId.value,
      userGroupIds: userGroupIds.value ? userGroupIds.value.split(',').map(s => s.trim()) : [],
      eventType: eventType.value,
      fields,
    };

    result.value = await simulateEvent(body);
  } catch (e) {
    error.value = e.message;
  } finally {
    sending.value = false;
  }
}
</script>

<template>
  <form @submit.prevent="handleSend" class="space-y-3 text-sm">
    <div class="grid grid-cols-2 gap-3">
      <div>
        <label class="mb-1 block text-slate-600">Event ID (auto if empty)</label>
        <input v-model="eventId" class="w-full rounded-lg border border-slate-200 p-2" placeholder="evt-001" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">Customer ID</label>
        <input v-model="customerId" class="w-full rounded-lg border border-slate-200 p-2" placeholder="customer-1" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">User ID</label>
        <input v-model="userId" class="w-full rounded-lg border border-slate-200 p-2" placeholder="user-1" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">Event Type</label>
        <input v-model="eventType" class="w-full rounded-lg border border-slate-200 p-2" placeholder="PRODUCT_VIEW" />
      </div>
      <div class="col-span-2">
        <label class="mb-1 block text-slate-600">User Group IDs (comma-separated)</label>
        <input v-model="userGroupIds" class="w-full rounded-lg border border-slate-200 p-2" placeholder="group-1,group-2" />
      </div>
    </div>

    <div>
      <div class="flex items-center justify-between mb-2">
        <span class="font-medium text-slate-600">Custom Fields</span>
        <button type="button" @click="addField" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200">+ Add Field</button>
      </div>
      <div class="space-y-2">
        <div v-for="(field, i) in customFields" :key="i" class="flex gap-2 items-center">
          <input v-model="field.key" class="flex-1 rounded-lg border border-slate-200 p-2" placeholder="key" />
          <input v-model="field.value" class="flex-1 rounded-lg border border-slate-200 p-2" placeholder="value" />
          <button type="button" @click="removeField(i)" class="text-rose-500 hover:text-rose-700 text-xs">Remove</button>
        </div>
      </div>
    </div>

    <div class="flex items-center gap-3">
      <button
        type="submit"
        :disabled="sending"
        class="rounded-lg bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
      >
        {{ sending ? 'Sending...' : '发送事件' }}
      </button>
      <span v-if="result" class="text-emerald-600">
        Event {{ result.eventId }} processed, {{ result.candidateStrategies }} candidate strategies
      </span>
      <span v-if="error" class="text-rose-600">Error: {{ error }}</span>
    </div>
  </form>
</template>
