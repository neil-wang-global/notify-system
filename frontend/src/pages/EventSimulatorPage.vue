<script setup>
import { ref } from 'vue';
import { simulateEvent } from '../api/events.js';

const eventId = ref('');
const customerId = ref('');
const userId = ref('');
const eventType = ref('');
const userGroupIds = ref('');
const customFields = ref([{ key: '', value: '' }]);
const occurredAt = ref('');

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

    if (occurredAt.value) {
      body.occurredAt = new Date(occurredAt.value).toISOString();
    }

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
        <label class="mb-1 block text-slate-600">事件ID（留空自动生成）</label>
        <input v-model="eventId" class="w-full rounded-lg border border-slate-200 p-2" placeholder="evt-001" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">客户ID</label>
        <input v-model="customerId" class="w-full rounded-lg border border-slate-200 p-2" placeholder="customer-1" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">用户ID</label>
        <input v-model="userId" class="w-full rounded-lg border border-slate-200 p-2" placeholder="user-1" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">事件类型</label>
        <input v-model="eventType" class="w-full rounded-lg border border-slate-200 p-2" placeholder="PRODUCT_VIEW" />
      </div>
      <div class="col-span-2">
        <label class="mb-1 block text-slate-600">用户组ID（逗号分隔）</label>
        <input v-model="userGroupIds" class="w-full rounded-lg border border-slate-200 p-2" placeholder="group-1,group-2" />
      </div>
      <div class="col-span-2">
        <label class="mb-1 block text-slate-600">发生时间（可选，留空使用当前时间）</label>
        <input v-model="occurredAt" type="datetime-local" class="w-full rounded-lg border border-slate-200 p-2" />
      </div>
    </div>

    <div>
      <div class="flex items-center justify-between mb-2">
        <span class="font-medium text-slate-600">自定义字段</span>
        <button type="button" @click="addField" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200">+ 添加字段</button>
      </div>
      <div class="space-y-2">
        <div v-for="(field, i) in customFields" :key="i" class="flex gap-2 items-center">
          <input v-model="field.key" class="flex-1 rounded-lg border border-slate-200 p-2" placeholder="键" />
          <input v-model="field.value" class="flex-1 rounded-lg border border-slate-200 p-2" placeholder="值" />
          <button type="button" @click="removeField(i)" class="text-rose-500 hover:text-rose-700 text-xs">删除</button>
        </div>
      </div>
    </div>

    <div class="flex items-center gap-3">
      <button
        type="submit"
        :disabled="sending"
        class="rounded-lg bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
      >
        {{ sending ? '发送中...' : '发送事件' }}
      </button>
      <span v-if="result" class="text-emerald-600">
        事件 {{ result.eventId }} 已处理
      </span>
      <span v-if="error" class="text-rose-600">错误: {{ error }}</span>
    </div>
  </form>
</template>
