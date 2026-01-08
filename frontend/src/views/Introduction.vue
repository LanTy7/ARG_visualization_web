<template>
  <div class="introduction-container">
    <el-card class="intro-card">
      <template #header>
        <div class="card-header">
          <el-icon class="header-icon"><InfoFilled /></el-icon>
          <span>项目介绍</span>
        </div>
      </template>
      
      <!-- ARG 模型介绍 -->
      <div class="section">
        <div class="section-header">
          <h2 class="section-title">
            <el-icon class="title-icon"><Cpu /></el-icon>
            ARG 模型介绍
          </h2>
        </div>
        
        <div class="section-content">
          <div class="content-block">
            <h3>模型概述</h3>
            <p>
              本系统采用基于深度学习的抗性基因（Antibiotic Resistance Gene, ARG）识别模型，
              能够快速准确地从DNA序列中识别出抗性基因。模型经过大量训练数据的训练和验证，
              在抗性基因识别任务上表现出色。
            </p>
          </div>
          
          <div class="content-block">
            <h3>技术特点</h3>
            <ul class="feature-list">
              <li>
                <strong>深度学习架构：</strong>
                采用先进的神经网络架构，能够捕捉序列中的复杂模式和特征
              </li>
              <li>
                <strong>高准确率：</strong>
                模型在测试集上的准确率达到95%以上，具有较高的可靠性
              </li>
              <li>
                <strong>快速处理：</strong>
                优化的算法设计使得模型能够快速处理大规模序列数据
              </li>
              <li>
                <strong>序列编码：</strong>
                使用k-mer编码和密码子编码等多种特征表示方法，充分提取序列信息
              </li>
            </ul>
          </div>
          
          
          
          <div class="content-block">
            <h3>处理流程</h3>
            <ol class="process-list">
              <li>序列预处理：对输入的DNA序列进行质量控制和去污染处理</li>
              <li>序列切割：使用6000bp窗口对序列进行切割</li>
              <li>特征提取：提取k-mer和密码子编码特征</li>
              <li>模型预测：使用训练好的模型进行抗性基因预测</li>
              <li>结果输出：生成预测结果和可视化报告</li>
            </ol>
          </div>
        </div>
      </div>
      
      <!-- 抗性基因介绍 -->
      <div class="section">
        <div class="section-header">
          <h2 class="section-title">
            <el-icon class="title-icon"><Document /></el-icon>
            抗性基因介绍
          </h2>
        </div>
        
        <div class="section-content">
          <div class="content-block">
            <h3>什么是抗性基因？</h3>
            <p>
              抗性基因（Antibiotic Resistance Genes, ARGs）是指能够使细菌对抗生素产生抗性的基因。
              这些基因编码的蛋白质可以通过多种机制使细菌对抗生素产生抗性，包括：
            </p>
            <ul class="feature-list">
              <li><strong>酶解作用：</strong>产生酶类降解抗生素分子</li>
              <li><strong>外排泵：</strong>将抗生素从细胞内排出</li>
              <li><strong>靶点修饰：</strong>改变抗生素作用的靶点结构</li>
              <li><strong>细胞壁改变：</strong>改变细胞壁结构阻止抗生素进入</li>
            </ul>
          </div>
          
          <div class="content-block">
            <h3>抗性基因的分类</h3>
            <p>
              抗性基因可以根据其对抗生素的作用机制和抗生素类型进行分类：
            </p>
            <div class="category-grid">
              <el-card class="category-card" shadow="hover">
                <template #header>
                  <span>β-内酰胺类</span>
                </template>
                <p>包括青霉素、头孢菌素等，主要通过产生β-内酰胺酶来抵抗抗生素</p>
              </el-card>
              
              <el-card class="category-card" shadow="hover">
                <template #header>
                  <span>氨基糖苷类</span>
                </template>
                <p>包括链霉素、卡那霉素等，通过修饰酶或改变核糖体结构产生抗性</p>
              </el-card>
              
              <el-card class="category-card" shadow="hover">
                <template #header>
                  <span>四环素类</span>
                </template>
                <p>包括四环素、多西环素等，主要通过外排泵或核糖体保护蛋白产生抗性</p>
              </el-card>
              
            </div>
          </div>
          
          <div class="content-block">
            <h3>抗性基因的重要性</h3>
            <p>
              抗性基因的传播是当前全球公共卫生面临的重大挑战。了解和研究抗性基因具有以下重要意义：
            </p>
            <ul class="feature-list">
              <li>
                <strong>临床诊断：</strong>
                帮助医生了解病原菌的耐药性，选择有效的抗生素治疗方案
              </li>
              <li>
                <strong>流行病学监测：</strong>
                追踪抗性基因的传播路径和流行趋势，制定防控策略
              </li>
              <li>
                <strong>新药研发：</strong>
                了解抗性机制有助于开发新型抗生素和抗性抑制剂
              </li>
              <li>
                <strong>环境监测：</strong>
                监测环境中抗性基因的分布，评估环境污染风险
              </li>
            </ul>
          </div>
          
          <div class="content-block">
            <h3>检测方法</h3>
            <p>
              传统的抗性基因检测方法包括PCR、基因芯片等，但这些方法通常只能检测已知的抗性基因。
              基于深度学习的计算方法能够：
            </p>
            <ul class="feature-list">
              <li>识别已知和未知的抗性基因序列</li>
              <li>处理大规模基因组数据</li>
              <li>提供快速、准确的预测结果</li>
              <li>降低检测成本和时间</li>
            </ul>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { InfoFilled, Cpu, Star, Document } from '@element-plus/icons-vue'
</script>

<style scoped>
.introduction-container {
  max-width: 1200px;
  margin: 0 auto;
}

.intro-card {
  background: linear-gradient(135deg, rgba(10, 25, 47, 0.95) 0%, rgba(17, 34, 64, 0.95) 100%);
  border: 1px solid rgba(0, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(0, 255, 255, 0.2);
  border-radius: 16px;
}

:deep(.intro-card .el-card__header) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.1) 0%, rgba(0, 150, 255, 0.1) 100%);
  border-bottom: 1px solid rgba(0, 255, 255, 0.3);
  padding: 20px 24px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 600;
  color: #00ffff;
  text-shadow: 0 0 15px rgba(0, 255, 255, 0.5);
}

.header-icon {
  font-size: 24px;
  filter: drop-shadow(0 0 8px rgba(0, 255, 255, 0.6));
}

.section {
  margin-bottom: 40px;
}

.section:last-child {
  margin-bottom: 0;
}

.section-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid rgba(0, 255, 255, 0.2);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #00ffff;
  text-shadow: 0 0 15px rgba(0, 255, 255, 0.4);
}

.title-icon {
  font-size: 28px;
  filter: drop-shadow(0 0 10px rgba(0, 255, 255, 0.6));
}

.section-content {
  color: rgba(255, 255, 255, 0.85);
  line-height: 1.8;
}

.content-block {
  margin-bottom: 32px;
}

.content-block:last-child {
  margin-bottom: 0;
}

.content-block h3 {
  font-size: 18px;
  font-weight: 600;
  color: #00ffff;
  margin: 0 0 16px 0;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.3);
}

.content-block p {
  margin: 0 0 16px 0;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.9;
}

.feature-list {
  margin: 16px 0;
  padding-left: 24px;
  color: rgba(255, 255, 255, 0.8);
}

.feature-list li {
  margin-bottom: 12px;
  line-height: 1.8;
}

.feature-list li strong {
  color: #00ffff;
  font-weight: 600;
}

.process-list {
  margin: 16px 0;
  padding-left: 24px;
  color: rgba(255, 255, 255, 0.8);
}

.process-list li {
  margin-bottom: 12px;
  line-height: 1.8;
}

.param-table {
  margin: 20px 0;
}

:deep(.param-table .el-descriptions) {
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.2);
  border-radius: 8px;
}

:deep(.param-table .el-descriptions__label) {
  color: rgba(0, 255, 255, 0.9);
  font-weight: 600;
}

:deep(.param-table .el-descriptions__content) {
  color: rgba(255, 255, 255, 0.8);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
  margin: 20px 0;
}

.category-card {
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.2);
  border-radius: 12px;
  transition: all 0.3s;
}

.category-card:hover {
  background: rgba(0, 255, 255, 0.1);
  border-color: rgba(0, 255, 255, 0.4);
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 255, 255, 0.2);
}

:deep(.category-card .el-card__header) {
  background: rgba(0, 255, 255, 0.1);
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  color: #00ffff;
  font-weight: 600;
  padding: 16px;
}

:deep(.category-card .el-card__body) {
  padding: 16px;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.7;
}

:deep(.el-divider__text) {
  background: transparent;
  color: #00ffff;
}

:deep(.el-divider) {
  border-color: rgba(0, 255, 255, 0.2);
  margin: 40px 0;
}
</style>

