package com.merge.merge.shared.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Shared, Redis-backed minimal task queue abstraction.
 * Used for asynchronously queuing jobs using Redis List operations.
 */
@Service
@RequiredArgsConstructor
public class RedisTaskQueue {

    private final StringRedisTemplate redisTemplate;

    /**
     * Pushes a task ID to the left side of the queue (FIFO enqueue).
     */
    public void enqueue(String queueName, String taskId) {
        redisTemplate.opsForList().leftPush(queueName, taskId);
    }

    /**
     * Pops a task ID from the right side of the queue (FIFO dequeue, non-blocking).
     */
    public String dequeue(String queueName) {
        return redisTemplate.opsForList().rightPop(queueName);
    }

    /**
     * Pops a task ID from the right side of the queue with a block timeout (blocking dequeue).
     */
    public String dequeue(String queueName, long timeout, TimeUnit unit) {
        return redisTemplate.opsForList().rightPop(queueName, timeout, unit);
    }
}
