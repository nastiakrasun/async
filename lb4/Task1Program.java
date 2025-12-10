package lb4;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Task1Program {

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        Task1ArrayFactorial task1 = new Task1ArrayFactorial(executor);

        // runAsync(): асинхронне вітальне повідомлення
        CompletableFuture<Void> intro = CompletableFuture.runAsync(() -> {
            System.out.println("=== Завдання 1: асинхронна робота з масивами та факторіалом ===");
        }, executor);

        Instant overallStart = Instant.now();

        CompletableFuture<Void> all = intro
                .thenComposeAsync(v -> task1.execute(), executor)
                .thenRunAsync(() -> {
                    // thenRunAsync(): викликається після завершення всього ланцюжка
                    Instant overallEnd = Instant.now();
                    Duration d = Duration.between(overallStart, overallEnd);
                    System.out.println("Загальний час роботи асинхронних операцій (завдання 1): "
                            + d.toMillis() + " ms");
                    System.out.println("=== Завдання 1 завершено ===");
                }, executor);

        all.join();
        executor.shutdown();
    }

    // Клас, який інкапсулює логіку завдання 1
    static class Task1ArrayFactorial {
        private final ExecutorService executor;
        private final Random random = new Random();

        public Task1ArrayFactorial(ExecutorService executor) {
            this.executor = executor;
        }

        public CompletableFuture<Void> execute() {
            // supplyAsync(): асинхронна генерація початкового масиву з 10 int
            CompletableFuture<int[]> initialArrayFuture = CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                int[] arr = new int[10];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = random.nextInt(10); // 0..9
                }
                long end = System.nanoTime();
                System.out.println("Початковий масив: " + Arrays.toString(arr));
                System.out.println("Час генерації масиву: " + formatNanos(end - start));
                return arr;
            }, executor);

            // thenApplyAsync(): створюємо новий масив, де кожен елемент збільшено на 5
            CompletableFuture<int[]> incrementedArrayFuture = initialArrayFuture.thenApplyAsync(initial -> {
                long start = System.nanoTime();
                int[] incremented = new int[initial.length];
                for (int i = 0; i < initial.length; i++) {
                    incremented[i] = initial[i] + 5;
                }
                long end = System.nanoTime();
                System.out.println("Масив +5: " + Arrays.toString(incremented));
                System.out.println("Час збільшення елементів на 5: " + formatNanos(end - start));
                return incremented;
            }, executor);

            // thenApplyAsync(): обчислення факторіала від суми другого масиву + сума першого
            CompletableFuture<BigInteger> factorialFuture = incrementedArrayFuture.thenApplyAsync(secondArray -> {
                long start = System.nanoTime();

                int[] firstArray = initialArrayFuture.join();
                int sumFirst = Arrays.stream(firstArray).sum();
                int sumSecond = Arrays.stream(secondArray).sum();
                int total = sumFirst + sumSecond;

                BigInteger factorial = factorial(BigInteger.valueOf(total));

                long end = System.nanoTime();
                System.out.println("Сума першого масиву: " + sumFirst);
                System.out.println("Сума другого масиву: " + sumSecond);
                System.out.println("Аргумент факторіалу (sum1 + sum2) = " + total);
                System.out.println("Час обчислення факторіалу: " + formatNanos(end - start));
                return factorial;
            }, executor);

            // thenAcceptAsync(): асинхронний вивід результату факторіалу
            return factorialFuture.thenAcceptAsync(result -> {
                System.out.println("Факторіал (sum1 + sum2) = " + result);
            }, executor);
        }

        private BigInteger factorial(BigInteger n) {
            BigInteger result = BigInteger.ONE;
            BigInteger i = BigInteger.ONE;
            while (i.compareTo(n) <= 0) {
                result = result.multiply(i);
                i = i.add(BigInteger.ONE);
            }
            return result;
        }

        private String formatNanos(long nanos) {
            double millis = nanos / 1_000_000.0;
            return String.format("%.3f ms", millis);
        }
    }
}
