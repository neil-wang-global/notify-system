<script setup>
import { ref, computed, onMounted, inject } from 'vue';
import { listStrategies, getStrategy, saveStrategy, updateStrategy, deleteStrategy } from '../api/strategies.js';

const userToken = inject('userToken');

// --- List state ---
const strategies = ref([]);
const loading = ref(true);
const listError = ref(null);
const initialLoadDone = ref(false);

// --- Form mode: 'hidden' | 'create' | 'edit' ---
const mode = ref('hidden');

// --- Form fields ---
const strategyId = ref('');
const name = ref('');
const scopeKind = ref('GLOBAL');
const scopeUserIds = ref('');
const scopeUserGroupIds = ref('');
const eventType = ref('');
const windowSize = ref('5m');
const threshold = ref(1);
const expectedVersion = ref(0);
const rules = ref([]);
const businessDedupWindowSeconds = ref('');
const dedupFields = ref('');

const saving = ref(false);
const formResult = ref(null);
const formError = ref(null);

// --- Validation ---
const validationErrors = ref({});

function validate() {
  const errors = {};
  if (!strategyId.value || !strategyId.value.trim()) {
    errors.strategyId = '策略ID不能为空';
  }
  if (!name.value || !name.value.trim()) {
    errors.name = '名称不能为空';
  }
  if (!['GLOBAL', 'USERS', 'USER_GROUPS'].includes(scopeKind.value)) {
    errors.scope = '范围必须为 GLOBAL / USERS / USER_GROUPS';
  }
  if (!eventType.value || !eventType.value.trim()) {
    errors.eventType = '事件类型不能为空';
  }
  if (threshold.value === null || threshold.value === '' || threshold.value <= 0) {
    errors.threshold = '阈值必须为正数';
  }
  if (!windowSize.value || !windowSize.value.trim()) {
    errors.windowSize = '窗口大小不能为空';
  }
  if (!userToken.value || !userToken.value.trim()) {
    errors.userToken = '用户Token不能为空';
  }
  if (scopeKind.value === 'USERS' && !scopeUserIds.value.trim()) {
    errors.scopeUserIds = '用户ID不能为空';
  }
  if (scopeKind.value === 'USER_GROUPS' && !scopeUserGroupIds.value.trim()) {
    errors.scopeUserGroupIds = '用户组ID不能为空';
  }
  validationErrors.value = errors;
  return Object.keys(errors).length === 0;
}

const hasValidationError = computed(() => Object.keys(validationErrors.value).length > 0);

const windowOptions = [
  { key: '5m', label: '5 分钟' },
  { key: '10m', label: '10 分钟' },
  { key: '30m', label: '30 分钟' },
  { key: '1d', label: '1 天' },
  { key: '5d', label: '5 天' },
];

// --- List helpers ---
async function fetchList() {
  loading.value = true;
  listError.value = null;
  try {
    strategies.value = await listStrategies();
  } catch (e) {
    listError.value = e.message;
  } finally {
    loading.value = false;
    initialLoadDone.value = true;
  }
}

async function handleDelete(id) {
  if (!confirm(`确定要删除策略 "${id}" 吗？`)) return;
  try {
    await deleteStrategy(id);
    await fetchList();
  } catch (e) {
    listError.value = e.message;
  }
}

// --- Form helpers ---
function resetForm() {
  strategyId.value = '';
  name.value = '';
  scopeKind.value = 'GLOBAL';
  scopeUserIds.value = '';
  scopeUserGroupIds.value = '';
  eventType.value = '';
  windowSize.value = '5m';
  threshold.value = 1;
  expectedVersion.value = 0;
  rules.value = [];
  businessDedupWindowSeconds.value = '';
  dedupFields.value = '';
  formResult.value = null;
  formError.value = null;
  validationErrors.value = {};
}

function openCreate() {
  resetForm();
  mode.value = 'create';
}

function cancelForm() {
  mode.value = 'hidden';
  resetForm();
}

async function openEdit(id) {
  resetForm();
  try {
    const s = await getStrategy(id);
    strategyId.value = s.strategyId || '';
    name.value = s.name || '';
    scopeKind.value = s.scope?.kind || 'GLOBAL';
    scopeUserIds.value = (s.scope?.userIds || []).join(', ');
    scopeUserGroupIds.value = (s.scope?.userGroupIds || []).join(', ');
    eventType.value = s.eventType || '';
    windowSize.value = s.windowSize || '5m';
    threshold.value = s.threshold ?? 1;
    expectedVersion.value = s.version ?? 0;
    const rawRules = s.rules || [];
    const isEventTypeOnlyMatch = rawRules.length === 1
      && rawRules[0].field === 'eventType'
      && rawRules[0].operator === 'EQ';
    rules.value = isEventTypeOnlyMatch ? [] : rawRules.map(r => ({
      field: r.field || '',
      operator: r.operator || 'EQ',
      value: Array.isArray(r.value) ? r.value.join(',') : (r.value || ''),
      connector: r.connector || 'AND',
      group: r.group || 'default',
      sortOrder: r.sortOrder ?? 0,
    }));
    if (isEventTypeOnlyMatch && rawRules[0].value) {
      eventType.value = String(rawRules[0].value);
    }
    businessDedupWindowSeconds.value = s.businessDedupWindowSeconds ?? '';
    dedupFields.value = Array.isArray(s.dedupFields) ? s.dedupFields.join(', ') : '';
    mode.value = 'edit';
  } catch (e) {
    listError.value = `加载策略失败: ${e.message}`;
  }
}

function addRule() {
  rules.value.push({ field: '', operator: 'EQ', value: '', connector: 'AND', group: 'default', sortOrder: rules.value.length + 1 });
}

function removeRule(index) {
  rules.value.splice(index, 1);
}

function buildBody() {
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
    eventType: eventType.value,
    rules: rules.value.length > 0 ? rules.value.map(r => ({
      field: r.field,
      operator: r.operator,
      value: r.value.includes(',') ? r.value.split(',').map(s => s.trim()) : r.value,
      connector: r.connector,
      group: r.group,
      sortOrder: r.sortOrder,
    })) : undefined,
    windowSize: windowSize.value,
    threshold: threshold.value,
    expectedVersion: expectedVersion.value,
    userToken: userToken.value,
    idempotencyKey: `${strategyId.value}-${crypto.randomUUID()}`,
  };

  if (businessDedupWindowSeconds.value !== '' && businessDedupWindowSeconds.value !== null) {
    body.businessDedupWindowSeconds = Number(businessDedupWindowSeconds.value);
  }
  if (dedupFields.value && dedupFields.value.trim()) {
    body.dedupFields = dedupFields.value.split(',').map(s => s.trim()).filter(Boolean);
  }

  return body;
}

async function handleSave() {
  formError.value = null;
  formResult.value = null;

  if (!validate()) return;

  saving.value = true;
  try {
    const body = buildBody();
    if (mode.value === 'create') {
      formResult.value = await saveStrategy(body);
    } else {
      formResult.value = await updateStrategy(strategyId.value, body);
    }
    mode.value = 'hidden';
    await fetchList();
  } catch (e) {
    formError.value = e.message;
  } finally {
    saving.value = false;
  }
}

onMounted(fetchList);
</script>

<template>
  <div class="space-y-4 text-sm">
    <!-- ---- List section ---- -->
    <div class="flex items-center gap-3">
      <button @click="openCreate" class="rounded-lg bg-blue-600 px-3 py-1 text-xs text-white hover:bg-blue-700">
        + 新建策略
      </button>
      <button @click="fetchList" :disabled="loading" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200 disabled:opacity-50">
        {{ loading ? '加载中...' : '刷新' }}
      </button>
    </div>

    <div v-if="listError" class="rounded-lg bg-rose-50 p-3 text-rose-600">错误: {{ listError }}</div>

    <!-- Loading skeleton -->
    <div v-if="loading && !initialLoadDone" class="space-y-2">
      <div class="h-10 rounded-lg bg-slate-100 animate-pulse"></div>
      <div class="h-10 rounded-lg bg-slate-100 animate-pulse"></div>
      <div class="h-10 rounded-lg bg-slate-100 animate-pulse"></div>
    </div>

    <div v-else-if="strategies.length === 0 && !loading" class="text-slate-400">暂无策略数据，请新建一个策略。</div>

    <div v-if="strategies.length > 0">
      <!-- Header row -->
      <div class="grid grid-cols-8 gap-2 rounded-lg bg-blue-50 p-3 font-medium text-blue-800">
        <span>策略ID</span><span>名称</span><span>范围</span><span>事件类型</span><span>窗口</span><span>阈值</span><span>版本</span><span>操作</span>
      </div>
      <!-- Data rows -->
      <div
        v-for="s in strategies"
        :key="s.strategyId"
        class="grid grid-cols-8 gap-2 rounded-lg border border-slate-200 bg-white p-3"
      >
        <span class="truncate">{{ s.strategyId }}</span>
        <span class="truncate">{{ s.name }}</span>
        <span>{{ s.scope?.kind || '-' }}</span>
        <span class="truncate">{{ s.eventType || '-' }}</span>
        <span>{{ s.windowSize }}</span>
        <span>{{ s.threshold }}</span>
        <span>{{ s.version }}</span>
        <span class="flex gap-2">
          <button @click="openEdit(s.strategyId)" class="rounded-lg bg-slate-100 px-2 py-0.5 text-xs hover:bg-slate-200">编辑</button>
          <button @click="handleDelete(s.strategyId)" class="rounded-lg bg-rose-50 px-2 py-0.5 text-xs text-rose-600 hover:bg-rose-100">删除</button>
        </span>
      </div>
    </div>

    <!-- ---- Form section (create / edit) ---- -->
    <div v-if="mode !== 'hidden'" class="rounded-lg border border-blue-200 bg-blue-50/30 p-4 space-y-4">
      <div class="flex items-center justify-between">
        <span class="font-medium text-slate-700">{{ mode === 'create' ? '新建策略' : '编辑策略' }}</span>
        <button @click="cancelForm" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200">取消</button>
      </div>

      <div v-if="formError" class="rounded-lg bg-rose-50 p-3 text-rose-600">错误: {{ formError }}</div>
      <div v-if="formResult" class="rounded-lg bg-emerald-50 p-3 text-emerald-600">已保存: {{ formResult.strategyId }} v{{ formResult.version }}</div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="mb-1 block text-slate-600">策略ID</label>
          <input v-model="strategyId" :disabled="mode === 'edit'" class="w-full rounded-lg border p-2 disabled:opacity-50" :class="validationErrors.strategyId ? 'border-rose-400' : 'border-slate-200'" placeholder="strategy-1" />
          <p v-if="validationErrors.strategyId" class="mt-1 text-xs text-rose-500">{{ validationErrors.strategyId }}</p>
        </div>
        <div>
          <label class="mb-1 block text-slate-600">名称</label>
          <input v-model="name" class="w-full rounded-lg border p-2" :class="validationErrors.name ? 'border-rose-400' : 'border-slate-200'" placeholder="我的策略" />
          <p v-if="validationErrors.name" class="mt-1 text-xs text-rose-500">{{ validationErrors.name }}</p>
        </div>
        <div>
          <label class="mb-1 block text-slate-600">范围</label>
          <select v-model="scopeKind" class="w-full rounded-lg border p-2" :class="validationErrors.scope ? 'border-rose-400' : 'border-slate-200'">
            <option value="GLOBAL">GLOBAL</option>
            <option value="USERS">USERS</option>
            <option value="USER_GROUPS">USER_GROUPS</option>
          </select>
          <p v-if="validationErrors.scope" class="mt-1 text-xs text-rose-500">{{ validationErrors.scope }}</p>
        </div>
        <div v-if="scopeKind === 'USERS'">
          <label class="mb-1 block text-slate-600">用户ID（逗号分隔）</label>
          <input v-model="scopeUserIds" class="w-full rounded-lg border p-2" :class="validationErrors.scopeUserIds ? 'border-rose-400' : 'border-slate-200'" placeholder="user-1,user-2" />
          <p v-if="validationErrors.scopeUserIds" class="mt-1 text-xs text-rose-500">{{ validationErrors.scopeUserIds }}</p>
        </div>
        <div v-if="scopeKind === 'USER_GROUPS'">
          <label class="mb-1 block text-slate-600">用户组ID（逗号分隔）</label>
          <input v-model="scopeUserGroupIds" class="w-full rounded-lg border p-2" :class="validationErrors.scopeUserGroupIds ? 'border-rose-400' : 'border-slate-200'" placeholder="group-1,group-2" />
          <p v-if="validationErrors.scopeUserGroupIds" class="mt-1 text-xs text-rose-500">{{ validationErrors.scopeUserGroupIds }}</p>
        </div>
        <div>
          <label class="mb-1 block text-slate-600">事件类型（必填）</label>
          <input v-model="eventType" class="w-full rounded-lg border p-2" :class="validationErrors.eventType ? 'border-rose-400' : 'border-slate-200'" placeholder="PRODUCT_VIEW" />
          <p v-if="validationErrors.eventType" class="mt-1 text-xs text-rose-500">{{ validationErrors.eventType }}</p>
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
          <p v-if="validationErrors.windowSize" class="mt-1 text-xs text-rose-500">{{ validationErrors.windowSize }}</p>
        </div>
        <div>
          <label class="mb-1 block text-slate-600">阈值</label>
          <input v-model.number="threshold" type="number" min="1" class="w-full rounded-lg border p-2" :class="validationErrors.threshold ? 'border-rose-400' : 'border-slate-200'" placeholder="1" />
          <p v-if="validationErrors.threshold" class="mt-1 text-xs text-rose-500">{{ validationErrors.threshold }}</p>
        </div>
      </div>

      <!-- Dedup config fields -->
      <div class="border-t border-slate-200 pt-3">
        <span class="font-medium text-slate-600">去重配置（可选）</span>
        <div class="mt-2 grid grid-cols-2 gap-3">
          <div>
            <label class="mb-1 block text-slate-600">业务去重窗口（秒）</label>
            <input v-model="businessDedupWindowSeconds" type="number" min="0" class="w-full rounded-lg border border-slate-200 p-2" placeholder="例如: 60" />
          </div>
          <div>
            <label class="mb-1 block text-slate-600">去重字段（逗号分隔）</label>
            <input v-model="dedupFields" class="w-full rounded-lg border border-slate-200 p-2" placeholder="例如: userId,productId" />
          </div>
        </div>
      </div>

      <!-- Rules -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <span class="font-medium text-slate-600">规则</span>
          <button @click="addRule" class="rounded-lg bg-slate-100 px-3 py-1 text-xs hover:bg-slate-200">+ 添加规则</button>
        </div>
        <div v-if="rules.length > 0" class="space-y-2">
          <div class="grid grid-cols-7 gap-2 rounded-lg bg-slate-50 p-2 text-xs font-medium text-slate-600">
            <span>字段</span><span>操作符</span><span>值</span><span>连接符</span><span>分组</span><span>排序</span><span></span>
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
            <button @click="removeRule(i)" class="text-rose-500 hover:text-rose-700 text-xs">删除</button>
          </div>
        </div>
        <p v-else class="text-xs text-slate-400">暂无规则配置，将仅使用事件类型进行匹配。</p>
      </div>

      <!-- Validation summary -->
      <div v-if="validationErrors.userToken" class="rounded-lg bg-amber-50 p-3 text-amber-700 text-xs">
        {{ validationErrors.userToken }}
      </div>

      <!-- Save button -->
      <div class="flex items-center gap-3">
        <button
          @click="handleSave"
          :disabled="saving || hasValidationError"
          class="rounded-lg bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          {{ saving ? '保存中...' : (mode === 'create' ? '创建策略' : '更新策略') }}
        </button>
      </div>
    </div>
  </div>
</template>
