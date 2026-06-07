<script setup>
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
</script>

<template>
  <main class="min-h-screen p-8">
    <header class="mb-8">
      <p class="text-sm font-semibold text-blue-600">Notify System Console</p>
      <h1 class="text-3xl font-bold">高并发事件通知系统</h1>
      <p class="mt-2 text-slate-600">策略配置、事件模拟、通知查询、异常补偿和压测展示。</p>
    </header>

    <section class="grid gap-4 lg:grid-cols-2">
      <article v-for="[title, Page] in pages" :key="title" class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 class="mb-4 text-xl font-semibold">{{ title }}</h2>
        <component :is="Page" />
      </article>
    </section>
  </main>
</template>
