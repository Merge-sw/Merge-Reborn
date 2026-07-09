package com.merge.merge.ai.service;

import com.merge.merge.shared.queue.RedisTaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class InstructorQueueWorker {

    private final RedisTaskQueue redisTaskQueue;
    private final InstructorService instructorService;
    private final TaskExecutor taskExecutor;

    private static final String QUEUE_NAME = "instructor:job:queue";

    public InstructorQueueWorker(
            RedisTaskQueue redisTaskQueue,
            InstructorService instructorService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.redisTaskQueue = redisTaskQueue;
        this.instructorService = instructorService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Polls the Redis queue every 1 second (1000ms).
     * Dequeues any pending jobs and spawns them asynchronously using the task executor.
     */
    @Scheduled(fixedDelay = 1000)
    public void pollQueue() {
        String taskId;
        while ((taskId = redisTaskQueue.dequeue(QUEUE_NAME)) != null) {
            final UUID id = UUID.fromString(taskId);
            log.info("Worker picked up job ID {} from queue. Dispatching to task executor...", id);

            taskExecutor.execute(() -> {
                try {
                    instructorService.processJob(id);
                } catch (Exception e) {
                    log.error("Unhandled exception in background processing of job ID " + id, e);
                }
            });
        }
    }
}
