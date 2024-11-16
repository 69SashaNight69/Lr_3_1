import java.util.*;
import java.util.concurrent.*;

public class MatrixMultiplication {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 1. Отримання параметрів від користувача з перевіркою введення
        int rows = getPositiveIntInput(scanner, "Введіть кількість рядків матриці A:");
        int cols = getPositiveIntInput(scanner, "Введіть кількість стовпців матриці A (і рядків матриці B):");
        int colsB = getPositiveIntInput(scanner, "Введіть кількість стовпців матриці B:");

        int min = getIntInput(scanner, "Введіть мінімальне значення елементів:");
        int max;
        do {
            max = getIntInput(scanner, "Введіть максимальне значення елементів:");
            if (max < min) {
                System.out.println("Максимальне значення не може бути меншим за мінімальне. Спробуйте ще раз.");
            }
        } while (max < min);

        // 2. Генерація матриць
        int[][] matrixA = generateMatrix(rows, cols, min, max);
        int[][] matrixB = generateMatrix(cols, colsB, min, max);

        // Вивід згенерованих матриць
        System.out.println("\nМатриця A:");
        printMatrix(matrixA);

        System.out.println("\nМатриця B:");
        printMatrix(matrixB);

        // 3. Виконання через Work stealing (Fork/Join Framework)
        ForkJoinPool pool = ForkJoinPool.commonPool();
        long start = System.nanoTime();
        int[][] resultForkJoin = pool.invoke(new MatrixTask(matrixA, matrixB, 0, rows));
        long end = System.nanoTime();
        System.out.println("\nРезультат (Work stealing):");
        printMatrix(resultForkJoin);
        System.out.println("Час виконання (Work stealing): " + (end - start) / 1_000_000 + " ms");

        // 4. Виконання через Work dealing (ExecutorService)
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        start = System.nanoTime();
        int[][] resultExecutor = multiplyWithExecutor(matrixA, matrixB, executor);
        end = System.nanoTime();
        executor.shutdown();
        System.out.println("\nРезультат (Work dealing):");
        printMatrix(resultExecutor);
        System.out.println("Час виконання (Work dealing): " + (end - start) / 1_000_000 + " ms");
    }

    // Метод для отримання додатного числа
    private static int getPositiveIntInput(Scanner scanner, String prompt) {
        int value;
        while (true) {
            System.out.println(prompt);
            if (scanner.hasNextInt()) {
                value = scanner.nextInt();
                if (value > 0) {
                    return value;
                } else {
                    System.out.println("Будь ласка, введіть додатне число.");
                }
            } else {
                System.out.println("Некоректне введення. Спробуйте ще раз.");
                scanner.next(); // очищення некоректного введення
            }
        }
    }

    // Метод для отримання будь-якого цілого числа
    private static int getIntInput(Scanner scanner, String prompt) {
        while (true) {
            System.out.println(prompt);
            if (scanner.hasNextInt()) {
                return scanner.nextInt();
            } else {
                System.out.println("Некоректне введення. Спробуйте ще раз.");
                scanner.next(); // очищення некоректного введення
            }
        }
    }

    // Генерація матриці
    private static int[][] generateMatrix(int rows, int cols, int min, int max) {
        Random random = new Random();
        int[][] matrix = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = random.nextInt(max - min + 1) + min;
            }
        }
        return matrix;
    }

    // Вивід матриці
    private static void printMatrix(int[][] matrix) {
        for (int[] row : matrix) {
            for (int value : row) {
                System.out.print(value + "\t");
            }
            System.out.println();
        }
    }

    // Fork/Join Task
    static class MatrixTask extends RecursiveTask<int[][]> {
        private final int[][] matrixA, matrixB;
        private final int startRow, endRow;

        MatrixTask(int[][] matrixA, int[][] matrixB, int startRow, int endRow) {
            this.matrixA = matrixA;
            this.matrixB = matrixB;
            this.startRow = startRow;
            this.endRow = endRow;
        }

        @Override
        protected int[][] compute() {
            int cols = matrixB[0].length;

            if (endRow - startRow <= 2) { // Якщо мало задач, рахуємо напряму
                int[][] result = new int[endRow - startRow][cols];
                for (int i = startRow; i < endRow; i++) {
                    for (int j = 0; j < cols; j++) {
                        for (int k = 0; k < matrixB.length; k++) {
                            result[i - startRow][j] += matrixA[i][k] * matrixB[k][j];
                        }
                    }
                }
                return result;
            }

            // Інакше ділимо задачу
            int mid = (startRow + endRow) / 2;
            MatrixTask topHalf = new MatrixTask(matrixA, matrixB, startRow, mid);
            MatrixTask bottomHalf = new MatrixTask(matrixA, matrixB, mid, endRow);
            invokeAll(topHalf, bottomHalf);
            int[][] topResult = topHalf.join();
            int[][] bottomResult = bottomHalf.join();
            return mergeResults(topResult, bottomResult);
        }

        private int[][] mergeResults(int[][] top, int[][] bottom) {
            int[][] result = new int[top.length + bottom.length][];
            System.arraycopy(top, 0, result, 0, top.length);
            System.arraycopy(bottom, 0, result, top.length, bottom.length);
            return result;
        }
    }

    // ExecutorService реалізація
    private static int[][] multiplyWithExecutor(int[][] matrixA, int[][] matrixB, ExecutorService executor) {
        int rows = matrixA.length;
        int cols = matrixB[0].length;
        int[][] result = new int[rows][cols];
        List<Future<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < rows; i++) {
            final int row = i;
            tasks.add(executor.submit(() -> {
                for (int j = 0; j < cols; j++) {
                    for (int k = 0; k < matrixB.length; k++) {
                        result[row][j] += matrixA[row][k] * matrixB[k][j];
                    }
                }
                return null;
            }));
        }

        for (Future<Void> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        return result;
    }
}
