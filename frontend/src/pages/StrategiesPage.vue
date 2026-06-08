<script setup>
import { ref, inject } from 'vue';
import { saveStrategy } from '../api/strategies.js';

const userToken = inject('userToken');

const strategyId = ref('');
const name = ref('');
const scopeKind = ref('GLOBAL');
const scopeUserIds = ref('');
const scopeUserGroupIds = ref('');
const eventType = ref('');
const windowSize = ref('5m');
const expectedVersion = ref(0);

const windowOptions = [
  { key: '5m', label: '5 分钟' },
  { key: '10m', label: '10 分钟' },
  { key: '30m', label: '30 分钟' },
  { key: '1d', label: '1 天' },
  { key: '5d', label: '5 天' },
];

const rules = ref([]);

const saving = ref(false);
const result = ref(null);
const error = ref(null);

function addRule() {
  rules.value.push({ field: '', operator: 'EQ', value: '', connector: 'AND', group: 'default', sortOrder: rules.value.length });
}

function removeRule(index) {
  rules.value.splice(index, 1);
}

async function handleSave() {
  saving.value = true;
  error.value = null;
  result.value = null;

  try {
    const scope = { kind: scopeKind.value };
    if (scopeKind.value === 'USERS') {
      scope.userIds = scopeUserIds.value.split(',').map(s => s.trim()).filter(Boolean);
      scope.userGroupIds = [];
    } else if (scopeKind.value === 'USER_GROUPS') {
      scope.userIds = [];
      scope.userGroupIds = scopeUserGroupIds.value.split(',').map(s => s.trim()).filter(Boolean);
    } else {
      scope.userIds = [];
      scope.userGroupIds = [];
    }

    const body = {
      strategyId: strategyId.value,
      name: name.value,
      scope,
      eventType: eventType.value || undefined,
      rules: rules.value.length > 0 ? rules.value.map(r => ({
        field: r.field,
        operator: r.operator,
        value: r.value.includes(',') ? r.value.split(',').map(s => s.trim()) : r.value,
        connector: r.connector,
        group: r.group,
        sortOrder: r.sortOrder,
      })) : undefined,
      windowSize: windowSize.value,
      expectedVersion: expectedVersion.value,
      userToken: userToken.value,
      idempotencyKey: `${strategyId.value}-${Date.now()}`,
    };

    result.value = await saveStrategy(body);
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="space-y-4 text-sm">
    <div class="grid grid-cols-2 gap-3">
      <div>
        <label class="mb-1 block text-slate-600">Strategy ID</label>
        <input v-model="strategyId" class="w-full rounded-lg border border-slate-200 p-2" placeholder="strategy-1" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">Name</label>
        <input v-model="name" class="w-full rounded-lg border border-slate-200 p-2" placeholder="连续浏览提醒" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">Scope</label>
        <select v-model="scopeKind" class="w-full rounded-lg border border-slate-200 p-2">
          <option value="GLOBAL">GLOBAL</option>
          <option value="USERS">USERS</option>
          <option value="USER_GROUPS">USER_GROUPS</option>
        </select>
      </div>
      <div v-if="scopeKind === 'USERS'">
        <label class="mb-1 block text-slate-600">User IDs (comma-separated)</label>
        <input v-model="scopeUserIds" class="w-full rounded-lg border border-slate-200 p-2" placeholder="user-1,user-2" />
      </div>
      <div v-if="scopeKind === 'USER_GROUPS'">
        <label class="mb-1 block text-slate-600">User Group IDs (comma-separated)</label>
        <input v-model="scopeUserGroupIds" class="w-full rounded-lg border border-slate-200 p-2" placeholder="group-1,group-2" />
      </div>
      <div v-if="rules.length === 0">
        <label class="mb-1 block text-slate-600">Event Type (used when no rules)</label>
        <input v-model="eventType" class="w-full rounded-lg border border-slate-200 p-2" placeholder="PRODUCT_VIEW" />
      </div>
      <div>
        <label class="mb-1 block text-slate-600">窗口大小</label>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="opt in windowOptions"
            :key="opt.key"
            @click="windowSize = opt.key"
            :class="[
              'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
              windowSize === opt.key
                ? 'bg-blue-600 text-white'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50',
            ]"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
      <div>
        <label class="mb-1 block text-slate-600">Expected Version (0 for create)</label>
        <input v-model.number="expectedVersion" type="number" class="w-full rounded-lg border border-slate-200 p-2" />
      </div>
    </div>

    <div>
      <div class="flex items-center justify-between mb-2">
        <span class="font-medium text-slate-600">Rules</span>
        <button @click="addRule" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200">+ Add Rule</button>
      </div>
      <div v-if="rules.length > 0" class="space-y-2">
        <div class="grid grid-cols-7 gap-2 rounded-lg bg-slate-50 p-2 text-xs font-medium text-slate-600">
          <span>Field</span><span>Operator</span><span>Value</span><span>Connector</span><span>Group</span><span>Sort</span><span></span>
        </div>
        <div v-for="(rule, i) in rules" :key="i" class="grid grid-cols-7 gap-2 items-center">
          <input v-model="rule.field" class="rounded-lg border border-slate-200 p-1.5 text-sm" placeholder="productId" />
          <select v-model="rule.operator" class="rounded-lg border border-slate-200 p-1.5 text-sm">
            <option>EQ</option><option>NE</option><option>IN</option><option>NOT_IN</option>
            <option>GT</option><option>GTE</option><option>LT</option><option>LTE</option>
            <option>BETWEEN</option><option>EXISTS</option><option>REGEX</option>
          </select>
          <input v-model="rule.value" class="rounded-lg border border-slate-200 p-1.5 text-sm" placeholder="P001,P002" />
          <select v-model="rule.connector" class="rounded-lg border border-slate-200 p-1.5 text-sm">
            <option>AND</option><option>OR</option>
          </select>
          <input v-model="rule.group" class="rounded-lg border border-slate-200 p-1.5 text-sm" placeholder="default" />
          <input v-model.number="rule.sortOrder" type="number" class="rounded-lg border border-slate-200 p-1.5 text-sm" />
          <button @click="removeRule(i)" class="text-rose-500 hover:text-rose-700 text-xs">Remove</button>
        </div>
      </div>
      <p v-else class="text-xs text-slate-400">No rules configured. Event type will be used for matching.</p>
    </div>

    <div class="flex items-center gap-3">
      <button
        @click="handleSave"
        :disabled="saving"
        class="rounded-lg bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
      >
        {{ saving ? 'Saving...' : '保存策略' }}
      </button>
      <span v-if="result" class="text-emerald-600">
        Saved: {{ result.strategyId }} v{{ result.version }}
      </span>
      <span v-if="error" class="text-rose-600">Error: {{ error }}</span>
    </div>
  </div>
</template>
