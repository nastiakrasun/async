package lb3;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

// ====== Інтерфейс стратегії підрахунку файлів ======
interface FileCounter {
    long countFiles(File rootDir, String extension);
}

// ====== Клас, що реалізує підрахунок через ForkJoin (work stealing) ======
class ForkJoinFileCounter implements FileCounter {

    /**
     * Завдання для рекурсивного обходу директорій
     */
    private static class DirectoryScanTask extends RecursiveTask<Long> {
        private final File directory;
        private final String extensionLower;

        public DirectoryScanTask(File directory, String extensionLower) {
            this.directory = directory;
            this.extensionLower = extensionLower;
        }

        @Override
        protected Long compute() {
            long count = 0L;
            List<DirectoryScanTask> subTasks = new ArrayList<>();

            File[] files = directory.listFiles();
            if (files == null) {
                // Нема доступу або не директорія
                return 0L;
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    // Для піддиректорії створюємо підзадачу
                    DirectoryScanTask task = new DirectoryScanTask(file, extensionLower);
                    task.fork(); // відправляємо в пул (може бути вкрадений іншим потоком)
                    subTasks.add(task);
                } else if (file.isFile()) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(extensionLower)) {
                        count++;
                    }
                }
            }

            // Додаємо результати всіх підзадач
            for (DirectoryScanTask task : subTasks) {
                count += task.join();
            }

            return count;
        }
    }

    @Override
    public long countFiles(File rootDir, String extension) {
        if (rootDir == null || !rootDir.isDirectory()) {
            throw new IllegalArgumentException("Початковий шлях має бути директорією.");
        }

        String normalizedExt = normalizeExtension(extension);

        ForkJoinPool pool = ForkJoinPool.commonPool();
        DirectoryScanTask rootTask = new DirectoryScanTask(rootDir, normalizedExt.toLowerCase());
        return pool.invoke(rootTask);
    }

    /**
     * Нормалізує введене розширення:
     * "pdf" -> ".pdf"
     * "*.pdf" -> ".pdf"
     * ".pdf" -> ".pdf"
     */
    private String normalizeExtension(String ext) {
        ext = ext.trim();
        if (ext.startsWith("*")) {
            ext = ext.substring(1); // прибрати *
        }
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        return ext;
    }
}

// ====== Утіліта для вимірювання часу ======
class TimeMeasureResult {
    private final long value;
    private final long nanos;

    public TimeMeasureResult(long value, long nanos) {
        this.value = value;
        this.nanos = nanos;
    }

    public long getValue() {
        return value;
    }

    public long getNanos() {
        return nanos;
    }

    public double getMillis() {
        return nanos / 1_000_000.0;
    }
}

// ====== Раннер, який запускає FileCounter з заміром часу ======
class FileCounterRunner {
    public TimeMeasureResult run(FileCounter counter, File rootDir, String extension) {
        long start = System.nanoTime();
        long result = counter.countFiles(rootDir, extension);
        long end = System.nanoTime();
        return new TimeMeasureResult(result, end - start);
    }
}

// ====== Головний клас (точка входу) ======
public class FileExtensionCounterForkJoin {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Введіть шлях до директорії: ");
        String dirPath = scanner.nextLine().trim();

        System.out.print("Введіть розширення файлів (наприклад: pdf, .pdf або *.pdf): ");
        String ext = scanner.nextLine().trim();
        scanner.close();

        File rootDir = new File(dirPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.out.println("Помилка: вказаний шлях не є існуючою директорією.");
            return;
        }

        FileCounter counter = new ForkJoinFileCounter();
        FileCounterRunner runner = new FileCounterRunner();

        System.out.println("\nПочинаємо пошук файлів з розширенням " + ext + " ...");

        TimeMeasureResult result = runner.run(counter, rootDir, ext);

        System.out.println("\n=== Результат ===");
        System.out.println("Кількість знайдених файлів: " + result.getValue());
        System.out.printf("Час виконання: %.3f ms (%d ns)%n",
                result.getMillis(), result.getNanos());
    }
}
