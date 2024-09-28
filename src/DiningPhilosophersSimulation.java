import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

class Philosopher extends Thread {
    private final int id;
    private final Fork leftFork;
    private final Fork rightFork;
    private Table currentTable;
    private final Random random = new Random();

    public Philosopher(int id, Fork leftFork, Fork rightFork, Table table) {
        this.id = id;
        this.leftFork = leftFork;
        this.rightFork = rightFork;
        this.currentTable = table;
    }

    public void updateTable(Table table) {
        this.currentTable = table;
    }

    private void updateActivityTime() {
        currentTable.updateLastActivityTime(); // Update last activity time for the table
    }

    private void think() throws InterruptedException {
        System.out.println("Philosopher " + id + " is thinking.");
        Thread.sleep(random.nextInt(100)); // Scaled-down thinking time (0-100ms)
        updateActivityTime(); // Update last activity time
    }

    private void eat() throws InterruptedException {
        System.out.println("Philosopher " + id + " is eating.");
        Thread.sleep(random.nextInt(50)); // Scaled-down eating time (0-50ms)
        updateActivityTime(); // Update last activity time
    }

    private boolean tryToPickUpForks() throws InterruptedException {
        Thread.sleep(random.nextInt(40)); // Scaled-down delay before picking up forks

        leftFork.lock(); // Lock the left fork
        try {
            while (!leftFork.isAvailable()) {
                System.out.println("Philosopher " + id + " is waiting for left fork.");
                leftFork.awaitAvailability(); // Wait for the left fork to become available
            }
            leftFork.pickUp(); // Philosopher picked up the left fork
            System.out.println("Philosopher " + id + " picked up left fork.");
            updateActivityTime();

            rightFork.lock(); // Lock the right fork
            try {
                while (!rightFork.isAvailable()) {
                    System.out.println("Philosopher " + id + " is waiting for right fork.");
                    rightFork.awaitAvailability(); // Wait for the right fork to become available
                }
                rightFork.pickUp(); // Philosopher picked up the right fork
                System.out.println("Philosopher " + id + " picked up right fork.");
                updateActivityTime();

                return true; // Successfully picked up both forks
            } finally {
                if (!rightFork.isHeldByCurrentThread()) {
                    leftFork.putDown();
                }
                rightFork.unlock(); // Always unlock the right fork
            }
        } finally {
            leftFork.unlock(); // Always unlock the left fork
        }
    }

    private void putDownForks() {
        leftFork.lock();
        try {
            leftFork.putDown();
            System.out.println("Philosopher " + id + " put down left fork.");
        } finally {
            leftFork.unlock();
        }

        rightFork.lock();
        try {
            rightFork.putDown();
            System.out.println("Philosopher " + id + " put down right fork.");
        } finally {
            rightFork.unlock();
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                think();
                if (tryToPickUpForks()) {
                    eat();
                    putDownForks();
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Philosopher " + id + " was interrupted.");
        }
    }

    public int getPhilosopherId() {
        return id;
    }
}

class Fork extends ReentrantLock {
    private final Condition forkAvailable = newCondition();
    private boolean available = true;
    private final int id;

    public Fork(int id) {
        this.id = id;
    }

    public boolean isAvailable() {
        return available;
    }

    public void pickUp() {
        available = false;
    }

    public void putDown() {
        available = true;
        forkAvailable.signal(); // Signal the next philosopher waiting for the fork
    }

    public void awaitAvailability() throws InterruptedException {
        forkAvailable.await(); // Wait for the fork to become available
    }

    @Override
    public String toString() {
        return "Fork " + id;
    }
}

class Table {
    private final int tableId;
    private final Philosopher[] philosophers;
    private final Fork[] forks;
    private final Table sixthTable;
    private long lastActivityTime; 
    private static int philosophersMovedToSixthTable = 0; // Track how many philosophers have moved to the sixth table
    private final Object lock = new Object();

    public Table(int tableId, Philosopher[] philosophers, Fork[] forks, Table sixthTable) {
        this.tableId = tableId;
        this.philosophers = philosophers;
        this.forks = forks;
        this.sixthTable = sixthTable;
        this.lastActivityTime = System.currentTimeMillis(); // Initialize with the current time
    }

    public synchronized void updateLastActivityTime() {
        this.lastActivityTime = System.currentTimeMillis(); // Update last activity time
    }

    public synchronized void checkDeadlock() {
        synchronized (lock) {
            long currentTime = System.currentTimeMillis();
            long inactivityDuration = currentTime - lastActivityTime;

            // If inactivity is detected, check if it's a deadlock
            if (inactivityDuration >= 190) { // Scaled down for faster simulation
                System.out.println("Deadlock detected at Table " + tableId + ". Moving philosopher to the sixth table.");
                moveToSixthTable(philosophers[0]);  // Move one philosopher to the sixth table to resolve deadlock
            }
        }
    }

    private void moveToSixthTable(Philosopher philosopher) {
        sixthTable.addPhilosopher(philosopher);
        philosopher.updateTable(sixthTable);
    }

    public void addPhilosopher(Philosopher philosopher) {
        System.out.println("Philosopher " + philosopher.getPhilosopherId() + " moved to the sixth table.================================================================================================================================================");
        for (int i = 0; i < philosophers.length; i++) {
            if (philosophers[i] == null) {
                philosophers[i] = philosopher;
                break;
            }
        }

        // Track how many philosophers have moved to the sixth table
        philosophersMovedToSixthTable++;

        // If the sixth table is full (5 philosophers), assume a deadlock at the sixth table
        if (philosophersMovedToSixthTable == 5) {
            System.out.println("Sixth table has entered deadlock. Exiting simulation.");
            System.exit(0);  // Exit the simulation when sixth table enters deadlock
        }
    }
}

class DeadlockDetector extends Thread {
    private final Table[] tables;

    public DeadlockDetector(Table[] tables) {
        this.tables = tables;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                for (Table table : tables) {
                    table.checkDeadlock();
                }
                Thread.sleep(300); // Check every 300 ms
            }
        } catch (InterruptedException e) {
            System.out.println("Deadlock detector interrupted.");
        }
    }
}

public class DiningPhilosophersSimulation {
    public static void main(String[] args) {
        int numberOfTables = 5;
        int numberOfPhilosophersPerTable = 5;
        Table[] tables = new Table[numberOfTables + 1];
        Table sixthTable = new Table(6, new Philosopher[5], new Fork[5], null);

        Thread[] philosopherThreads = new Thread[numberOfTables * numberOfPhilosophersPerTable];

        for (int tableId = 1; tableId <= numberOfTables; tableId++) {
            Fork[] forks = new Fork[numberOfPhilosophersPerTable];
            Philosopher[] philosophers = new Philosopher[numberOfPhilosophersPerTable];

            for (int i = 0; i < numberOfPhilosophersPerTable; i++) {
                forks[i] = new Fork(i);
            }

            for (int i = 0; i < numberOfPhilosophersPerTable; i++) {
                Fork leftFork = forks[i];
                Fork rightFork = forks[(i + 1) % numberOfPhilosophersPerTable];
                philosophers[i] = new Philosopher(i + (tableId - 1) * numberOfPhilosophersPerTable, leftFork, rightFork, null);
                philosopherThreads[(tableId - 1) * numberOfPhilosophersPerTable + i] = philosophers[i];
            }
            tables[tableId - 1] = new Table(tableId, philosophers, forks, sixthTable);

            for (int i = 0; i < numberOfPhilosophersPerTable; i++) {
                philosophers[i].updateTable(tables[tableId - 1]);
            }
        }
        tables[numberOfTables] = sixthTable;

        for (Thread philosopherThread : philosopherThreads) {
            philosopherThread.start();
        }

        DeadlockDetector deadlockDetector = new DeadlockDetector(tables);
        deadlockDetector.start();

        try {
            Thread.sleep(100000); // Sleep for 100 seconds (100,000ms)
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted.");
        }

        System.out.println("Interrupting all threads...");

        for (Thread philosopherThread : philosopherThreads) {
            philosopherThread.interrupt();
        }

        deadlockDetector.interrupt();

        try {
            for (Thread philosopherThread : philosopherThreads) {
                philosopherThread.join();
            }
            deadlockDetector.join();
        } catch (InterruptedException e) {
            System.out.println("Simulation interrupted.");
        }

        System.out.println("Simulation finished.");
    }
}
