<script setup>
import { ref, provide } from 'vue';
import StrategiesPage from './pages/StrategiesPage.vue';
import RuleEditorPage from './pages/RuleEditorPage.vue';
import EventSimulatorPage from './pages/EventSimulatorPage.vue';
import NotificationsPage from './pages/NotificationsPage.vue';
import ExceptionsPage from './pages/ExceptionsPage.vue';
import BenchmarkReportPage from './pages/BenchmarkReportPage.vue';
import SystemMonitorPage from './pages/SystemMonitorPage.vue';

const pages = [
  ['策略管理', StrategiesPage],
  ['规则编辑', RuleEditorPage],
  ['事件模拟', EventSimulatorPage],
  ['通知记录', NotificationsPage],
  ['异常中心', ExceptionsPage],
  ['压测结果', BenchmarkReportPage],
  ['系统监控', SystemMonitorPage],
];

const activeTab = ref(0);
const userToken = ref('console-user');
provide('userToken', userToken);
</script>

<template>
  <main class="min-h-screen p-8">
    <header class="mb-6">
      <p class="text-sm font-semibold text-blue-600">Notify System Console</p>
      <h1 class="text-3xl font-bold">高并发事件通知系统</h1>
      <p class="mt-2 text-slate-600">策略配置、事件模拟、通知查询、异常补偿和压测展示。</p>
      <div class="mt-3 flex items-center gap-2">
        <label class="text-sm text-slate-600">User Token:</label>
        <input
          v-model="userToken"
          class="rounded-lg border border-slate-200 px-3 py-1.5 text-sm"
          placeholder="console-user"
        />
      </div>
    </header>

    <nav class="mb-6 flex flex-wrap gap-2">
      <button
        v-for="(page, idx) in pages"
        :key="idx"
        @click="activeTab = idx"
        :class="[
          'rounded-lg px-4 py-2 text-sm font-medium transition-colors',
          activeTab === idx
            ? 'bg-blue-600 text-white'
            : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50',
        ]"
      >
        {{ page[0] }}
      </button>
    </nav>

    <section class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 class="mb-4 text-xl font-semibold">{{ pages[activeTab][0] }}</h2>
      <component :is="pages[activeTab][1]" />
    </section>
  </main>
</template>
