<script setup>
import { ref } from 'vue';

const rules = ref([
  { field: '', operator: 'EQ', value: '', connector: 'AND', group: 'default', sortOrder: 0 },
]);

function addRule() {
  rules.value.push({
    field: '',
    operator: 'EQ',
    value: '',
    connector: 'AND',
    group: 'default',
    sortOrder: rules.value.length,
  });
}

function removeRule(index) {
  rules.value.splice(index, 1);
}

const emit = defineEmits(['rules-built']);

function buildRules() {
  const built = rules.value.map(r => ({
    field: r.field,
    operator: r.operator,
    value: r.value.includes(',') ? r.value.split(',').map(s => s.trim()) : r.value,
    connector: r.connector,
    group: r.group,
    sortOrder: r.sortOrder,
  }));
  emit('rules-built', built);
}

const operators = ['EQ', 'NE', 'IN', 'NOT_IN', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN', 'EXISTS', 'REGEX'];
</script>

<template>
  <div class="space-y-3 text-sm">
    <div class="flex items-center justify-between">
      <span class="font-medium text-slate-600">Rule Rows</span>
      <button @click="addRule" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200">+ Add Row</button>
    </div>

    <div class="grid grid-cols-7 gap-2 rounded-lg bg-slate-50 p-3 font-medium text-slate-600">
      <span>字段</span><span>算子</span><span>值</span><span>连接</span><span>分组</span><span>排序</span><span></span>
    </div>

    <div v-for="(rule, i) in rules" :key="i" class="grid grid-cols-7 gap-2 items-center">
      <input v-model="rule.field" class="rounded-lg border border-slate-200 p-1.5" placeholder="productId" />
      <select v-model="rule.operator" class="rounded-lg border border-slate-200 p-1.5">
        <option v-for="op in operators" :key="op" :value="op">{{ op }}</option>
      </select>
      <input v-model="rule.value" class="rounded-lg border border-slate-200 p-1.5" placeholder="P001,P002" />
      <select v-model="rule.connector" class="rounded-lg border border-slate-200 p-1.5">
        <option>AND</option><option>OR</option>
      </select>
      <input v-model="rule.group" class="rounded-lg border border-slate-200 p-1.5" placeholder="default" />
      <input v-model.number="rule.sortOrder" type="number" class="rounded-lg border border-slate-200 p-1.5" />
      <button @click="removeRule(i)" class="text-rose-500 hover:text-rose-700 text-xs">Remove</button>
    </div>

    <button @click="buildRules" class="rounded-lg bg-blue-600 px-3 py-2 text-white">
      Build Rules Array
    </button>
    <p class="text-xs text-slate-400 mt-1">
      Use this editor to design rule rows, then copy the built array into a strategy save on the Strategies page.
    </p>
  </div>
</template>
