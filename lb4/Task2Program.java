package lb4;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Task2Program {

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        Task2MinAdjacentSums task2 = new Task2MinAdjacentSums(executor);

        // runAsync(): асинхронне стартове повідомлення
        CompletableFuture<Void> intro = CompletableFuture.runAsync(() -> {
            System.out.println("=== Завдання 2: мінімум сум сусідніх елементів ===");
        }, executor);

        Instant overallStart = Instant.now();

        CompletableFuture<Void> all = intro
                .thenComposeAsync(v -> task2.execute(), executor)
                .thenRunAsync(() -> {
                    // thenRunAsync(): викликається після завершення завдання 2
                    Instant overallEnd = Instant.now();
                    Duration d = Duration.between(overallStart, overallEnd);
                    System.out.println("Загальний час роботи асинхронних операцій (завдання 2): "
                            + d.toMillis() + " ms");
                    System.out.println("=== Завдання 2 завершено ===");
                }, executor);

        all.join();
        executor.shutdown();
    }

    // Клас, який інкапсулює логіку завдання 2
    static class Task2MinAdjacentSums {
        private final ExecutorService executor;
        private final Random random = new Random();

        public Task2MinAdjacentSums(ExecutorService executor) {
            this.executor = executor;
        }

        public CompletableFuture<Void> execute() {

            // supplyAsync(): асинхронне генерування послідовності
            CompletableFuture<int[]> sequenceFuture = CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                int[] arr = new int[20];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = random.nextInt(50) + 1; // натуральні числа 1..50
                }
                long end = System.nanoTime();
                System.out.println("Початкова послідовність (20 елементів): " + Arrays.toString(arr));
                System.out.println("Час генерації послідовності: " + formatNanos(end - start));
                return arr;
            }, executor);

            // thenApplyAsync(): обчислення мінімальної суми сусідніх елементів
            CompletableFuture<Integer> minSumFuture = sequenceFuture.thenApplyAsync(arr -> {
                long start = System.nanoTime();

                if (arr.length < 2) {
                    throw new IllegalStateException("Послідовність надто коротка для обчислення сум сусідніх елементів.");
                }

                int minSum = Integer.MAX_VALUE;
                for (int i = 0; i < arr.length - 1; i++) {
                    int sum = arr[i] + arr[i + 1];
                    if (sum < minSum) {
                        minSum = sum;
                    }
                }

                long end = System.nanoTime();
                System.out.println("Мінімальне значення серед сум ai + ai+1: " + minSum);
                System.out.println("Час обчислення мінімальної суми: " + formatNanos(end - start));
                return minSum;
            }, executor);

            // thenAcceptAsync(): вивід результату з інформаційним повідомленням
            return minSumFuture.thenAcceptAsync(minSum -> {
                System.out.println("Результат (min(a_i + a_{i+1})): " + minSum);
            }, executor);
        }

        private String formatNanos(long nanos) {
            double millis = nanos / 1_000_000.0;
            return String.format("%.3f ms", millis);
        }
    }
}
