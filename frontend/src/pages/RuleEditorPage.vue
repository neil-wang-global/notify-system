<template>
  <div class="space-y-3 text-sm">
    <div class="grid grid-cols-5 gap-2 rounded-lg bg-slate-50 p-3 font-medium text-slate-600">
      <span>字段</span><span>算子</span><span>值</span><span>连接</span><span>分组</span>
    </div>
    <div class="grid grid-cols-5 gap-2 rounded-lg border border-slate-100 p-3">
      <span>productId</span><span>IN</span><span>P001,P002</span><span>AND</span><span>group-1</span>
    </div>
    <div class="grid grid-cols-5 gap-2 rounded-lg border border-slate-100 p-3">
      <span>channel</span><span>EQ</span><span>APP</span><span>OR</span><span>group-1</span>
    </div>
  </div>
</template>
