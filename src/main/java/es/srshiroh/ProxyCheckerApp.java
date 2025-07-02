package es.srshiroh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * Aplicación principal del ProxyChecker
 * Soporta modo consola y modo GUI
 */
public class ProxyCheckerApp {
    private static final Logger logger = LoggerFactory.getLogger(ProxyCheckerApp.class);

    public static void main(String[] args) {
        System.out.println("=== ProxyChecker v1.0 by SrShiroh ===");
        System.out.println();

        // Verificar argumentos
        if (args.length > 0) {
            if (args[0].equals("--help") || args[0].equals("-h")) {
                showHelp();
                return;
            } else if (args[0].equals("--gui")) {
                startGUI();
                return;
            } else if (args[0].equals("--console")) {
                startConsoleMode(args);
                return;
            } else {
                // Asumir que es un archivo de proxies
                startConsoleMode(args);
                return;
            }
        }

        // Sin argumentos, preguntar modo
        chooseMode();
    }

    private static void showHelp() {
        System.out.println("Uso: java -jar proxychecker.jar [opciones] [archivo]");
        System.out.println();
        System.out.println("Opciones:");
        System.out.println("  --gui              Iniciar en modo gráfico");
        System.out.println("  --console [archivo] Iniciar en modo consola");
        System.out.println("  --help, -h         Mostrar esta ayuda");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  java -jar proxychecker.jar --gui");
        System.out.println("  java -jar proxychecker.jar --console proxies.txt");
        System.out.println("  java -jar proxychecker.jar proxies.txt");
        System.out.println();
        System.out.println("Formato del archivo de proxies:");
        System.out.println("  ip:puerto");
        System.out.println("  ip:puerto:tipo");
        System.out.println("  Ejemplo: 192.168.1.1:8080:HTTP");
    }

    private static void chooseMode() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Seleccione el modo de ejecución:");
        System.out.println("1. Modo gráfico (GUI)");
        System.out.println("2. Modo consola");
        System.out.print("Opción (1-2): ");

        try {
            int choice = Integer.parseInt(scanner.nextLine().trim());

            switch (choice) {
                case 1:
                    startGUI();
                    break;
                case 2:
                    startConsoleInteractive();
                    break;
                default:
                    System.out.println("Opción inválida. Iniciando modo gráfico por defecto...");
                    startGUI();
            }
        } catch (NumberFormatException e) {
            System.out.println("Opción inválida. Iniciando modo gráfico por defecto...");
            startGUI();
        }
    }

    private static void startGUI() {
        try {
            System.out.println("Iniciando interfaz gráfica...");
            ProxyCheckerGUI.main(new String[0]);
        } catch (Exception e) {
            logger.error("Error iniciando GUI", e);
            System.err.println("Error iniciando interfaz gráfica: " + e.getMessage());
            System.out.println("Cambiando a modo consola...");
            startConsoleInteractive();
        }
    }

    private static void startConsoleMode(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--console"))) {
            startConsoleInteractive();
            return;
        }

        String filename = args[args.length - 1]; // Último argumento es el archivo

        if (!new File(filename).exists()) {
            System.err.println("Error: Archivo no encontrado: " + filename);
            System.exit(1);
        }

        System.out.println("Iniciando verificación en modo consola...");
        System.out.println("Archivo: " + filename);
        System.out.println();

        ProxyManager manager = new ProxyManager();
        CountDownLatch latch = new CountDownLatch(1);

        // Configurar callbacks
        manager.setOnProxyChecked(proxy -> {
            System.out.printf("✓ %s - %s%n",
                proxy.getAddress(),
                proxy.getStatusString());
        });

        manager.setOnStatusUpdate(status -> {
            System.out.println(">> " + status);
        });

        manager.setOnCompleted(() -> {
            System.out.println();
            System.out.println("=== VERIFICACIÓN COMPLETADA ===");
            System.out.println(manager.getStatistics());

            // Exportar automáticamente los válidos
            try {
                String outputFile = "valid_proxies_" + System.currentTimeMillis() + ".txt";
                manager.exportValidProxiesToFile(outputFile);
                System.out.println("Proxies válidos guardados en: " + outputFile);
            } catch (IOException e) {
                System.err.println("Error guardando proxies válidos: " + e.getMessage());
            }

            latch.countDown();
        });

        try {
            manager.loadProxiesFromFile(filename);

            // Esperar a que termine
            latch.await();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            logger.error("Error en modo consola", e);
            System.exit(1);
        }
    }

    private static void startConsoleInteractive() {
        Scanner scanner = new Scanner(System.in);
        ProxyManager manager = new ProxyManager();

        System.out.println("=== MODO CONSOLA INTERACTIVO ===");
        System.out.println();

        // Configuración
        System.out.print("Timeout en milisegundos (default 10000): ");
        String timeoutInput = scanner.nextLine().trim();
        if (!timeoutInput.isEmpty()) {
            try {
                manager.setTimeout(Integer.parseInt(timeoutInput));
            } catch (NumberFormatException e) {
                System.out.println("Timeout inválido, usando default (10000ms)");
            }
        }

        System.out.print("Número de hilos (default 50): ");
        String threadsInput = scanner.nextLine().trim();
        if (!threadsInput.isEmpty()) {
            try {
                manager.setThreadCount(Integer.parseInt(threadsInput));
            } catch (NumberFormatException e) {
                System.out.println("Número de hilos inválido, usando default (50)");
            }
        }

        System.out.print("¿Verificar HTTPS? (s/N): ");
        String httpsInput = scanner.nextLine().trim().toLowerCase();
        manager.setTestHttps(httpsInput.equals("s") || httpsInput.equals("si") || httpsInput.equals("yes"));

        // Archivo de proxies
        String filename;
        while (true) {
            System.out.print("Archivo de proxies: ");
            filename = scanner.nextLine().trim();

            if (filename.isEmpty()) {
                System.out.println("Debe especificar un archivo");
                continue;
            }

            if (!new File(filename).exists()) {
                System.out.println("Archivo no encontrado: " + filename);
                continue;
            }

            break;
        }

        CountDownLatch latch = new CountDownLatch(1);

        // Configurar callbacks para mostrar progreso
        manager.setOnProxyChecked(proxy -> {
            if (proxy.isValid()) {
                System.out.printf("✅ %s (%dms)%n", proxy.getAddress(), proxy.getResponseTime());
            } else {
                System.out.printf("❌ %s - %s%n", proxy.getAddress(),
                    proxy.getErrorMessage() != null ? proxy.getErrorMessage() : "Invalid");
            }
        });

        manager.setOnStatusUpdate(status -> {
            System.out.println(">> " + status);
        });

        manager.setOnCompleted(() -> {
            System.out.println();
            System.out.println("=== VERIFICACIÓN COMPLETADA ===");
            System.out.println(manager.getStatistics());

            // Preguntar si exportar
            System.out.print("¿Exportar proxies válidos? (S/n): ");
            String exportChoice = scanner.nextLine().trim().toLowerCase();

            if (!exportChoice.equals("n") && !exportChoice.equals("no")) {
                System.out.print("Nombre del archivo (default: valid_proxies.txt): ");
                String outputFile = scanner.nextLine().trim();
                if (outputFile.isEmpty()) {
                    outputFile = "valid_proxies.txt";
                }

                try {
                    manager.exportValidProxiesToFile(outputFile);
                    System.out.println("✅ Proxies válidos guardados en: " + outputFile);
                } catch (IOException e) {
                    System.err.println("❌ Error guardando: " + e.getMessage());
                }
            }

            latch.countDown();
        });

        System.out.println();
        System.out.println("Iniciando verificación...");
        System.out.println("Presione Ctrl+C para cancelar");
        System.out.println();

        try {
            manager.loadProxiesFromFile(filename);
            latch.await();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            logger.error("Error en modo interactivo", e);
        }

        scanner.close();
    }
}
