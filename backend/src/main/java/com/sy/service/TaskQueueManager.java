package com.sy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 任务队列管理器
 * 确保分析任务按顺序执行，避免并发冲突
 */
@Slf4j
@Component
public class TaskQueueManager {

    @Value("${docker.genomad.max-concurrent:1}")
    private int maxConcurrent;

    @Value("${analysis.queue-size:100}")
    private int queueSize;

    private ExecutorService executorService;
    private final ConcurrentHashMap<Long, Future<?>> runningTasks = new ConcurrentHashMap<>();

    /**
     * 初始化线程池
     */
    public void init() {
        if (executorService == null) {
            // 创建有限线程池，控制并发数
            executorService = new ThreadPoolExecutor(
                    maxConcurrent,                    // 核心线程数
                    maxConcurrent,                    // 最大线程数
                    60L,                               // 空闲线程存活时间
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueSize),  // 任务队列
                    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：由调用线程执行
            );
            log.info("任务队列管理器初始化完成，最大并发数: {}, 队列大小: {}", maxConcurrent, queueSize);
        }
    }

    /**
     * 提交任务到队列
     * @param taskId 任务ID
     * @param task 要执行的任务
     */
    public void submitTask(Long taskId, Runnable task) {
        init(); // 确保线程池已初始化
        
        log.info("提交任务到队列: taskId={}", taskId);
        
        // 检查任务是否已在运行
        if (runningTasks.containsKey(taskId)) {
            log.warn("任务已在队列中: taskId={}", taskId);
            return;
        }
        
        // 包装任务，添加日志和清理逻辑
        Runnable wrappedTask = () -> {
            try {
                log.info("开始执行任务: taskId={}", taskId);
                task.run();
                log.info("任务执行完成: taskId={}", taskId);
            } catch (Exception e) {
                log.error("任务执行失败: taskId={}", taskId, e);
            } finally {
                // 任务完成后从运行列表中移除
                runningTasks.remove(taskId);
            }
        };
        
        // 提交任务
        Future<?> future = executorService.submit(wrappedTask);
        runningTasks.put(taskId, future);
        
        log.info("任务已加入队列: taskId={}, 当前队列大小: {}", taskId, runningTasks.size());
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public boolean cancelTask(Long taskId) {
        Future<?> future = runningTasks.get(taskId);
        if (future != null && !future.isDone()) {
            log.info("取消任务: taskId={}", taskId);
            boolean cancelled = future.cancel(true);
            runningTasks.remove(taskId);
            return cancelled;
        }
        return false;
    }

    /**
     * 获取队列状态
     */
    public Map<String, Object> getQueueStatus() {
        init();
        
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
        Map<String, Object> status = new HashMap<>();
        
        status.put("activeCount", tpe.getActiveCount());           // 正在执行的任务数
        status.put("queueSize", tpe.getQueue().size());            // 队列中等待的任务数
        status.put("completedTaskCount", tpe.getCompletedTaskCount());  // 已完成的任务数
        status.put("taskCount", tpe.getTaskCount());               // 总任务数
        status.put("runningTasks", runningTasks.size());           // 运行中的任务数
        
        return status;
    }

    /**
     * 检查任务是否在运行
     */
    public boolean isTaskRunning(Long taskId) {
        Future<?> future = runningTasks.get(taskId);
        return future != null && !future.isDone();
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("关闭任务队列管理器");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

