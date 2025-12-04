import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Модель фрагмента масиву (шматок, який буде обробляти конкретний потік)
class ArrayChunk {
    private final int startIndex;
    private final int endIndex;

    public ArrayChunk(int startIndex, int endIndex) {
        if (startIndex > endIndex) {
            throw new IllegalArgumentException("startIndex не може бути більшим за endIndex");
        }
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }
}

// Завдання: помножити частину масиву на множник і повернути новий підмасив
class ArrayChunkTask implements Callable<int[]> {

    private final int[] source;
    private final ArrayChunk chunk;
    private final int factor;

    public ArrayChunkTask(int[] source, ArrayChunk chunk, int factor) {
        this.source = source;
        this.chunk = chunk;
        this.factor = factor;
    }

    @Override
    public int[] call() {
        String threadName = Thread.currentThread().getName();
        System.out.println("Потік " + threadName + " обробляє індекси [" +
                chunk.getStartIndex() + "; " + chunk.getEndIndex() + "]");

        int length = chunk.getEndIndex() - chunk.getStartIndex() + 1;
        int[] result = new int[length];

        for (int i = 0; i < length; i++) {
            int sourceIndex = chunk.getStartIndex() + i;
            result[i] = source[sourceIndex] * factor;
        }

        return result;
    }
}

// Менеджер, який керує:
// - розбиттям масиву на частини
// - запуском потоків
// - збором результатів через Future та CopyOnWriteArrayList
class ArrayMultiplicationManager {

    private final int[] sourceArray;
    private final int factor;
    private final int threadCount;

    public ArrayMultiplicationManager(int[] sourceArray, int factor, int threadCount) {
        if (sourceArray == null) {
            throw new IllegalArgumentException("sourceArray не може бути null");
        }
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount має бути > 0");
        }
        this.sourceArray = sourceArray;
        this.factor = factor;
        this.threadCount = threadCount;
    }

    public int[] multiplyArray() {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<int[]>> futures = new ArrayList<>();

        int length = sourceArray.length;
        List<ArrayChunk> chunks = splitIntoChunks(length, threadCount);

        // Створюємо задачі для кожного шматка
        for (ArrayChunk chunk : chunks) {
            ArrayChunkTask task = new ArrayChunkTask(sourceArray, chunk, factor);
            Future<int[]> future = executor.submit(task);
            futures.add(future);
        }

        // Збираємо результати у потокобезпечний список
        CopyOnWriteArrayList<int[]> partialResults = new CopyOnWriteArrayList<>();

        for (Future<int[]> future : futures) {
            try {
                int[] part = future.get();

                System.out.println("Завдання виконано? " + future.isDone()
                        + ", скасовано? " + future.isCancelled());

                if (!future.isCancelled()) {
                    partialResults.add(part);
                }
            } catch (InterruptedException e) {
                System.out.println("Потік було перервано під час очікування результату: " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                System.out.println("Помилка виконання одного з завдань: " + e.getMessage());
            }
        }

        executor.shutdown();

        // Обʼєднуємо всі підмасиви в один результатний масив
        return mergePartialResults(partialResults);
    }

    // Розбиваємо масив на приблизно рівні шматки
    private List<ArrayChunk> splitIntoChunks(int length, int parts) {
        List<ArrayChunk> chunks = new ArrayList<>();

        int chunkSize = (length + parts - 1) / parts;
        for (int i = 0; i < parts; i++) {
            int startIndex = i * chunkSize;
            if (startIndex >= length) {
                break;
            }
            int endIndex = Math.min(startIndex + chunkSize - 1, length - 1);
            chunks.add(new ArrayChunk(startIndex, endIndex));
        }

        return chunks;
    }

    // Збираємо всі int[] у один великий int[]
    private int[] mergePartialResults(List<int[]> partialResults) {
        int totalLength = 0;
        for (int[] part : partialResults) {
            totalLength += part.length;
        }

        int[] resultArray = new int[totalLength];
        int pos = 0;
        for (int[] part : partialResults) {
            System.arraycopy(part, 0, resultArray, pos, part.length);
            pos += part.length;
        }
        return resultArray;
    }
}

// Головний клас (тільки робота з користувачем, запуск менеджера і вивід)
public class ArrayMultiplierApp {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 1. Створюємо масив від -100 до 100
        int[] sourceArray = createArrayFromRange(-100, 100);

        System.out.print("Введіть множник: ");
        int factor = scanner.nextInt();

        // 2. Вибираємо кількість потоків (простий варіант)
        int threadCount = chooseThreadCount(sourceArray.length);
        System.out.println("Обрана кількість потоків: " + threadCount);

        long startTime = System.currentTimeMillis();

        ArrayMultiplicationManager manager =
                new ArrayMultiplicationManager(sourceArray, factor, threadCount);
        int[] resultArray = manager.multiplyArray();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 3. Вивід
        System.out.println("Початковий масив:");
        printArray(sourceArray);

        System.out.println("Результат множення на " + factor + ":");
        printArray(resultArray);

        System.out.println("Час роботи програми: " + duration + " мс");
        scanner.close();
    }

    // Обираємо кількість потоків
    private static int chooseThreadCount(int length) {
        int availableCpus = Runtime.getRuntime().availableProcessors();
        int baseThreads;

        if (length <= 50) {
            baseThreads = 1;
        } else if (length <= 100) {
            baseThreads = 2;
        } else {
            baseThreads = 4;
        }

        int threadCount = Math.min(baseThreads, availableCpus);
        return Math.max(threadCount, 1);
    }

    // Створити масив з діапазону [from; to]
    private static int[] createArrayFromRange(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from не може бути більшим за to");
        }
        int length = to - from + 1;
        int[] arr = new int[length];
        int value = from;
        for (int i = 0; i < length; i++) {
            arr[i] = value++;
        }
        return arr;
    }

    // Красивий вивід масиву
    private static void printArray(int[] array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        System.out.println(sb);
    }
}
