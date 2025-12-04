package lb3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.*;

// ======== Інтерфейс стратегії обчислення попарної суми =========
interface PairSumCalculator {
    long compute(int[] array);
    String getName();
}

// ======== Генератор масивів =========
class ArrayGenerator {
    public int[] generate(int length, int minValue, int maxValue) {
        if (length < 2) {
            throw new IllegalArgumentException("Довжина масиву має бути >= 2");
        }
        if (minValue > maxValue) {
            int tmp = minValue;
            minValue = maxValue;
            maxValue = tmp;
        }

        int[] array = new int[length];
        Random random = new Random();

        int bound = maxValue - minValue + 1; // для nextInt

        for (int i = 0; i < length; i++) {
            array[i] = minValue + random.nextInt(bound);
        }
        return array;
    }
}

// ======== Work Stealing реалізація через ForkJoin =========
class WorkStealingPairSumCalculator implements PairSumCalculator {

    private static class PairSumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 20_000; // поріг для переходу до послідовного виконання

        private final int[] array;
        private final int start; // індекс першої пари (i для (a[i]+a[i+1]))
        private final int end;   // EXCLUSIVE – НЕ включається

        public PairSumTask(int[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            int length = end - start;
            if (length <= THRESHOLD) {
                long sum = 0L;
                // пари: (a[i] + a[i+1]) для i в [start, end)
                for (int i = start; i < end; i++) {
                    sum += (long) array[i] + array[i + 1];
                }
                return sum;
            } else {
                int mid = start + length / 2;
                PairSumTask left = new PairSumTask(array, start, mid);
                PairSumTask right = new PairSumTask(array, mid, end);
                left.fork();                  // асинхронно
                long rightResult = right.compute(); // поточний потік
                long leftResult = left.join();
                return leftResult + rightResult;
            }
        }
    }

    @Override
    public long compute(int[] array) {
        // кількість пар = array.length - 1
        ForkJoinPool pool = ForkJoinPool.commonPool();
        PairSumTask task = new PairSumTask(array, 0, array.length - 1);
        return pool.invoke(task);
    }

    @Override
    public String getName() {
        return "Work Stealing (ForkJoinPool)";
    }
}

// ======== Work Dealing реалізація через ExecutorService =========
class WorkDealingPairSumCalculator implements PairSumCalculator {

    @Override
    public long compute(int[] array) {
        int pairCount = array.length - 1;

        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            int chunkSize = (pairCount + numThreads - 1) / numThreads; // ділимо більш-менш рівномірно

            List<Future<Long>> futures = new ArrayList<>();

            int start = 0;
            while (start < pairCount) {
                int end = Math.min(start + chunkSize, pairCount);
                int from = start;
                int to = end;

                Callable<Long> task = () -> {
                    long sum = 0L;
                    for (int i = from; i < to; i++) {
                        sum += (long) array[i] + array[i + 1];
                    }
                    return sum;
                };

                futures.add(executor.submit(task));
                start = end;
            }

            long total = 0L;
            for (Future<Long> f : futures) {
                total += f.get();
            }
            return total;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Виконання перервано", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Помилка в одному з потоків", e);
        } finally {
            executor.shutdown();
        }
    }

    @Override
    public String getName() {
        return "Work Dealing (Fixed Thread Pool)";
    }
}

// ======== Утіліта для вимірювання часу =========
class TimeMeasureResult {
    private final long result;
    private final long nanos;

    public TimeMeasureResult(long result, long nanos) {
        this.result = result;
        this.nanos = nanos;
    }

    public long getResult() {
        return result;
    }

    public long getNanos() {
        return nanos;
    }

    public double getMillis() {
        return nanos / 1_000_000.0;
    }
}

class CalculatorRunner {
    public TimeMeasureResult run(PairSumCalculator calculator, int[] array) {
        long start = System.nanoTime();
        long result = calculator.compute(array);
        long end = System.nanoTime();
        return new TimeMeasureResult(result, end - start);
    }
}

// ======== Головний клас (точка входу) =========
public class PairwiseSumComparison {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Введіть кількість елементів масиву (>=2): ");
        int n = scanner.nextInt();

        System.out.print("Введіть початкове значення діапазону: ");
        int minValue = scanner.nextInt();

        System.out.print("Введіть кінцеве значення діапазону: ");
        int maxValue = scanner.nextInt();

        ArrayGenerator generator = new ArrayGenerator();
        int[] array = generator.generate(n, minValue, maxValue);

        // Ініціалізуємо стратегії
        PairSumCalculator workStealing = new WorkStealingPairSumCalculator();
        PairSumCalculator workDealing = new WorkDealingPairSumCalculator();
        CalculatorRunner runner = new CalculatorRunner();

        // Запуск обох версій
        System.out.println("\n=== Обчислення попарної суми ( (a1+a2)+(a2+a3)+...+(an-1+an) ) ===");

        TimeMeasureResult wsResult = runner.run(workStealing, array);
        System.out.printf(
                "%s:\n  Результат: %d\n  Час: %.3f ms (%d ns)\n\n",
                workStealing.getName(),
                wsResult.getResult(),
                wsResult.getMillis(),
                wsResult.getNanos()
        );

        TimeMeasureResult wdResult = runner.run(workDealing, array);
        System.out.printf(
                "%s:\n  Результат: %d\n  Час: %.3f ms (%d ns)\n\n",
                workDealing.getName(),
                wdResult.getResult(),
                wdResult.getMillis(),
                wdResult.getNanos()
        );

        // Перевірка, що результати збігаються
        if (wsResult.getResult() == wdResult.getResult()) {
            System.out.println("✅ Обидві версії дають однаковий результат.");
        } else {
            System.out.println("⚠ Увага! Результати різняться, перевірте реалізацію.");
        }

        // Порівняння часу
        if (wsResult.getNanos() < wdResult.getNanos()) {
            System.out.println("Work Stealing вийшов швидшим на цьому запуску.");
        } else if (wsResult.getNanos() > wdResult.getNanos()) {
            System.out.println("Work Dealing вийшов швидшим на цьому запуску.");
        } else {
            System.out.println("Час виконання практично однаковий.");
        }
        scanner.close();
    }
}
