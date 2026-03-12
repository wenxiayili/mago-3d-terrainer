package com.gaia3d.terrain.tile;

import com.gaia3d.command.GlobalOptions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
public class GaiaThreadPool {
    private static GaiaThreadPool instance;

    private final ExecutorService executorService;
    private final int multiThreadCount;

    public GaiaThreadPool(int threadCount) {
        this.multiThreadCount = threadCount;
        this.executorService = Executors.newFixedThreadPool(threadCount);
    }

    public static synchronized GaiaThreadPool getInstance() {
        if (instance == null || instance.executorService.isShutdown()) {
            int threadCount = Math.max(1, GlobalOptions.getInstance().getThreadCount());
            instance = new GaiaThreadPool(threadCount);
        }
        return instance;
    }

    public void execute(List<Runnable> tasks) throws InterruptedException {
        List<Future<?>> futures = new ArrayList<>(tasks.size());
        for (Runnable task : tasks) {
            futures.add(executorService.submit(task));
        }
        try {
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    log.error("A parallel task failed.", e.getCause());
                    futures.stream().filter(f -> !f.isDone()).forEach(f -> f.cancel(true));
                    throw new RuntimeException(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    futures.stream().filter(f -> !f.isDone()).forEach(f -> f.cancel(true));
                    throw e;
                }
            }
        } finally {
            executorService.shutdown();
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
