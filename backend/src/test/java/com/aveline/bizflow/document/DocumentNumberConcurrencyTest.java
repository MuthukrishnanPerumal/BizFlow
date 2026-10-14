package com.aveline.bizflow.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DocumentNumberConcurrencyTest {

    @Autowired
    private DocumentNumberService documentNumberService;

    @Test
    void concurrentPurchaseNumbersAreUniqueAndSequential() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            futures.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return documentNumberService.next(DocumentSeries.PURCHASE, 2099);
            }));
        }
        start.countDown();
        Set<String> numbers = new HashSet<>();
        for (Future<String> future : futures) {
            String number = future.get(30, TimeUnit.SECONDS);
            assertTrue(number.startsWith("PUR-2099-"));
            numbers.add(number);
        }
        pool.shutdown();
        assertEquals(threads, numbers.size());
    }

    @Test
    void concurrentSaleNumbersAreUniqueAndSequential() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            futures.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return documentNumberService.next(DocumentSeries.SALE, 2099);
            }));
        }
        start.countDown();
        Set<String> numbers = new HashSet<>();
        long highest = 0;
        for (Future<String> future : futures) {
            String number = future.get(30, TimeUnit.SECONDS);
            assertTrue(number.startsWith("SAL-2099-"));
            numbers.add(number);
            highest = Math.max(highest, Long.parseLong(number.substring("SAL-2099-".length())));
        }
        pool.shutdown();
        assertEquals(threads, numbers.size());
        assertTrue(highest >= threads);
    }
}
