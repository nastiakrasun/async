package lb1;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internet Orders Simulation
 * - Admin adds products
 * - Buyers purchase products
 * - Semaphores control stock
 * - Store has open/close windows (working hours)
 *
 * Run: javac Main.java && java Main
 */
public class Main {

    // Pretty time for logs
    static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // ------------ Domain ------------

    // Товар з семафором як лічильником залишку
    static class Product {
        final String name;
        final Semaphore stock; // permits == units in stock

        Product(String name, int initialStock) {
            this.name = name;
            this.stock = new Semaphore(Math.max(initialStock, 0), true);
        }

        // Адмін додає штучки в наявність
        void addStock(int amount) {
            if (amount <= 0) return;
            stock.release(amount);
        }

        // Покупець пробує купити одиницю
        boolean tryBuy(long timeoutMs) throws InterruptedException {
            return stock.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        }

        int available() { return stock.availablePermits(); }
    }

    // Магазин: каталоги, робочі години
    static class Store {
        private final Map<String, Product> catalog = new ConcurrentHashMap<>();
        private final AtomicBoolean open = new AtomicBoolean(false);

        void addProduct(Product p) { catalog.put(p.name, p); }
        Product getProduct(String name) { return catalog.get(name); }

        boolean isOpen() { return open.get(); }
        void setOpen(boolean value) { open.set(value); }

        Collection<Product> all() { return catalog.values(); }
    }

    // ------------ Actors (threads) ------------

    // Керує "робочими годинами": open X sec -> close Y sec -> повтор
    static class WorkingHours implements Runnable {
        private final Store store;
        private final long openMs, closeMs;

        WorkingHours(Store store, long openMs, long closeMs) {
            this.store = store;
            this.openMs = openMs;
            this.closeMs = closeMs;
        }

        @Override public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    store.setOpen(true);
                    log("STORE", "OPENED");
                    Thread.sleep(openMs);

                    store.setOpen(false);
                    log("STORE", "CLOSED");
                    Thread.sleep(closeMs);
                }
            } catch (InterruptedException ie) {
                log("STORE", "WorkingHours interrupted, stopping.");
                Thread.currentThread().interrupt();
            }
        }
    }

    // Адміністратор: додає товари хвилями
    static class Admin implements Runnable {
        private final Store store;
        private final Random rnd = new Random();

        Admin(Store store) { this.store = store; }

        @Override public void run() {
            log("ADMIN", "STARTED");
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // періодично поповнюємо випадковий товар
                    Product p = randomProduct();
                    int add = 1 + rnd.nextInt(3); // 1..3
                    p.addStock(add);
                    log("ADMIN", String.format("Added %d x %s (stock=%d)", add, p.name, p.available()));
                    Thread.sleep(1200); // поповнення раз на ~1.2с
                }
            } catch (InterruptedException ie) {
                log("ADMIN", "INTERRUPTED");
                Thread.currentThread().interrupt();
            }
        }

        private Product randomProduct() {
            List<Product> list = new ArrayList<>(store.all());
            return list.get(rnd.nextInt(list.size()));
        }
    }

    // Покупець: намагається купувати, якщо відкрито
    static class Buyer implements Runnable {
        private final String name;
        private final Store store;
        private final String desiredProduct;
        private final Random rnd = new Random();

        Buyer(String name, Store store, String desiredProduct) {
            this.name = name;
            this.store = store;
            this.desiredProduct = desiredProduct;
        }

        @Override public void run() {
            log(name, "STARTED");
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (!store.isOpen()) {
                        log(name, "WAITING (store closed)");
                        Thread.sleep(300); // чекаємо відкриття
                        continue;
                    }

                    // Обираємо бажаний товар (або інший випадково)
                    Product p = pickProduct();
                    // Пауза імітує навігацію сайтом
                    Thread.sleep(150 + rnd.nextInt(200));

                    // Спроба купити: якщо дефіцит — швидко тайм-аутимося
                    boolean ok = p.tryBuy(200);
                    if (ok) {
                        log(name, String.format("BOUGHT 1 x %s (left=%d)", p.name, p.available()));
                        // "оформлення" замовлення
                        Thread.sleep(120 + rnd.nextInt(200));
                    } else {
                        log(name, String.format("SOLD OUT: %s (left=%d) — will try later",
                                p.name, p.available()));
                        Thread.sleep(250);
                    }
                }
            } catch (InterruptedException ie) {
                log(name, "INTERRUPTED");
                Thread.currentThread().interrupt();
            }
        }

        private Product pickProduct() {
            // 70% — те, що хочемо; 30% — інший товар
            if (rnd.nextDouble() < 0.7) {
                Product p = store.getProduct(desiredProduct);
                if (p != null) return p;
            }
            List<Product> list = new ArrayList<>(store.all());
            return list.get(rnd.nextInt(list.size()));
        }
    }

    // ------------ Bootstrap ------------

    public static void main(String[] args) throws Exception {
        Store store = new Store();
        // Початковий каталог і наявність
        store.addProduct(new Product("Laptop", 1));
        store.addProduct(new Product("Headphones", 2));
        store.addProduct(new Product("Mouse", 0));  // свідомо 0 — щоб побачити SOLD OUT
        store.addProduct(new Product("Keyboard", 1));

        // Плануємо роботу: магазин відкритий 2.5с, зачинений 1.5с (циклічно)
        Thread hours = new Thread(new WorkingHours(store, 2500, 1500), "WorkingHours");
        Thread admin = new Thread(new Admin(store), "Admin");

        // Кілька покупців із різними вподобаннями
        List<Thread> buyers = List.of(
                new Thread(new Buyer("Buyer-1", store, "Laptop")),
                new Thread(new Buyer("Buyer-2", store, "Mouse")),
                new Thread(new Buyer("Buyer-3", store, "Headphones")),
                new Thread(new Buyer("Buyer-4", store, "Keyboard"))
        );

        // Старт
        hours.start();
        admin.start();
        buyers.forEach(Thread::start);

        // Дамо симуляції попрацювати ~10 секунд
        Thread.sleep(5_000);

        // Коректне завершення
        buyers.forEach(Thread::interrupt);
        admin.interrupt();
        hours.interrupt();

        // Приєднуємося
        for (Thread t : buyers) t.join();
        admin.join();
        hours.join();

        log("MAIN", "Simulation finished. Final stock:");
        for (Product p : store.all()) {
            log("STOCK", p.name + " -> " + p.available());
        }
    }

    // ------------ Helpers ------------

    static void log(String who, String msg) {
        System.out.printf("[%s] %-10s | %s%n", LocalTime.now().format(TF), who, msg);
    }
}
