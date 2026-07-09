package com.merge.merge.identity;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.identity.models.Student;
import com.merge.merge.identity.repository.StudentRepository;
import com.merge.merge.identity.service.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that awardXp is atomic under concurrent calls. The fix replaced
 * the read-modify-write (getById → addXp → save) with a single MongoDB
 * findAndModify + $inc. Ten concurrent awards of 10 XP each must always
 * produce exactly 100 XP — no lost-update race is possible with $inc.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AwardXpConcurrencyTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    @AfterEach
    void cleanUp() {
        studentRepository.deleteAll();
    }

    @Test
    void concurrentAwardXpProducesExactTotal() throws Exception {
        Student student = studentService.create("Ada", "details", UUID.randomUUID());
        UUID studentId = student.getId();

        int threads = 10;
        int xpPerAward = 10;
        int expectedTotal = threads * xpPerAward; // 100

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // all threads wait here to maximize contention
                    studentService.awardXp(studentId, xpPerAward);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        ready.await();
        start.countDown(); // release all threads simultaneously
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        int actualXp = studentService.getById(studentId).getXp();

        System.out.printf("[AwardXpConcurrencyTest] expected=%d actual=%d%n", expectedTotal, actualXp);
        assertThat(actualXp).isEqualTo(expectedTotal);
    }
}
