package es.srshiroh;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Gestor principal para la verificación de proxies
 */
public class ProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(ProxyManager.class);

    // Configuración por defecto
    private static final int DEFAULT_TIMEOUT = 10000; // 10 segundos
    private static final int DEFAULT_THREADS = 50;
    private static final String TEST_URL = "http://httpbin.org/ip";
    private static final String TEST_HTTPS_URL = "https://httpbin.org/ip";

    // Estado del manager
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isCanceled = new AtomicBoolean(false);
    private final AtomicInteger checkedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);

    // Configuración
    private int timeout = DEFAULT_TIMEOUT;
    private int threadCount = DEFAULT_THREADS;
    private String testUrl = TEST_URL;
    private boolean testHttps = true;

    // Datos
    private final List<ProxyInfo> workingProxies = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> processedProxies = Collections.synchronizedSet(new HashSet<>());

    // Ejecutor de hilos
    private ExecutorService executor;

    // Callbacks
    private Consumer<ProxyInfo> onProxyChecked;
    private Consumer<String> onStatusUpdate;
    private Runnable onCompleted;

    public ProxyManager() {
        this.executor = Executors.newFixedThreadPool(threadCount);
    }

    // Métodos de configuración
    public void setTimeout(int timeout) {
        this.timeout = Math.max(1000, timeout);
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = Math.max(1, Math.min(200, threadCount));
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        this.executor = Executors.newFixedThreadPool(this.threadCount);
    }

    public void setTestUrl(String testUrl) {
        this.testUrl = testUrl != null ? testUrl : TEST_URL;
    }

    public void setTestHttps(boolean testHttps) {
        this.testHttps = testHttps;
    }

    // Callbacks
    public void setOnProxyChecked(Consumer<ProxyInfo> callback) {
        this.onProxyChecked = callback;
    }

    public void setOnStatusUpdate(Consumer<String> callback) {
        this.onStatusUpdate = callback;
    }

    public void setOnCompleted(Runnable callback) {
        this.onCompleted = callback;
    }

    // Métodos principales
    public void loadProxiesFromFile(String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            throw new FileNotFoundException("Archivo no encontrado: " + filename);
        }

        List<ProxyInfo> proxies = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Ignorar líneas vacías y comentarios
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

                try {
                    ProxyInfo proxy = ProxyInfo.fromString(line);
                    String key = proxy.getHost() + ":" + proxy.getPort();

                    if (!processedProxies.contains(key)) {
                        proxies.add(proxy);
                        processedProxies.add(key);
                    }
                } catch (Exception e) {
                    logger.warn("Error procesando línea {}: {} - {}", lineNumber, line, e.getMessage());
                }
            }
        }

        totalCount.set(proxies.size());
        updateStatus("Cargados " + proxies.size() + " proxies desde " + filename);

        // Comenzar verificación
        checkProxies(proxies);
    }

    public void addProxy(String proxyString) {
        try {
            ProxyInfo proxy = ProxyInfo.fromString(proxyString);
            String key = proxy.getHost() + ":" + proxy.getPort();

            if (!processedProxies.contains(key)) {
                processedProxies.add(key);
                totalCount.incrementAndGet();
                checkProxy(proxy);
            }
        } catch (Exception e) {
            logger.error("Error añadiendo proxy: " + proxyString, e);
        }
    }

    private void checkProxies(List<ProxyInfo> proxies) {
        for (ProxyInfo proxy : proxies) {
            if (isCanceled.get()) {
                break;
            }

            executor.submit(() -> checkProxy(proxy));
        }
    }

    private void checkProxy(ProxyInfo proxy) {
        try {
            // Esperar si está pausado
            while (isPaused.get() && !isCanceled.get()) {
                Thread.sleep(100);
            }

            if (isCanceled.get()) {
                return;
            }

            long startTime = System.currentTimeMillis();
            boolean isValid = false;
            String errorMessage = null;

            try {
                // Verificar conectividad básica
                if (testBasicConnectivity(proxy)) {
                    // Verificar HTTP
                    if (testHttpRequest(proxy)) {
                        isValid = true;

                        // Si está habilitado, también probar HTTPS
                        if (testHttps && proxy.getType() != ProxyInfo.ProxyType.SOCKS4) {
                            testHttpsRequest(proxy);
                        }
                    }
                }
            } catch (Exception e) {
                errorMessage = e.getMessage();
                logger.debug("Error verificando proxy {}: {}", proxy.getAddress(), e.getMessage());
            }

            long responseTime = System.currentTimeMillis() - startTime;

            proxy.setValid(isValid);
            proxy.setResponseTime(responseTime);
            proxy.setErrorMessage(errorMessage);
            proxy.setLastChecked(LocalDateTime.now());

            if (isValid) {
                workingProxies.add(proxy);
            }

            // Callback
            if (onProxyChecked != null) {
                onProxyChecked.accept(proxy);
            }

            int checked = checkedCount.incrementAndGet();
            int total = totalCount.get();

            updateStatus(String.format("Progreso: %d/%d (%d válidos)",
                checked, total, getValidProxyCount()));

            // Verificar si hemos terminado
            if (checked >= total) {
                if (onCompleted != null) {
                    onCompleted.run();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean testBasicConnectivity(ProxyInfo proxy) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(proxy.getHost(), proxy.getPort()), timeout);
            return true;
        } catch (Exception e) {
            proxy.setErrorMessage("Conexión fallida: " + e.getMessage());
            return false;
        }
    }

    private boolean testHttpRequest(ProxyInfo proxy) {
        try {
            RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeout))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout))
                .build();

            HttpHost proxyHost = new HttpHost(proxy.getHost(), proxy.getPort());

            try (CloseableHttpClient client = HttpClients.custom()
                    .setDefaultRequestConfig(config)
                    .setProxy(proxyHost)
                    .build()) {

                HttpGet request = new HttpGet(testUrl);
                try (CloseableHttpResponse response = client.execute(request)) {
                    return response.getCode() == 200;
                }
            }
        } catch (Exception e) {
            proxy.setErrorMessage("HTTP test failed: " + e.getMessage());
            return false;
        }
    }

    private boolean testHttpsRequest(ProxyInfo proxy) {
        try {
            RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeout))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout))
                .build();

            HttpHost proxyHost = new HttpHost(proxy.getHost(), proxy.getPort());

            try (CloseableHttpClient client = HttpClients.custom()
                    .setDefaultRequestConfig(config)
                    .setProxy(proxyHost)
                    .build()) {

                HttpGet request = new HttpGet(TEST_HTTPS_URL);
                try (CloseableHttpResponse response = client.execute(request)) {
                    if (response.getCode() == 200) {
                        proxy.setAnonymous(true); // Si puede hacer HTTPS, probablemente sea anónimo
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("HTTPS test failed for {}: {}", proxy.getAddress(), e.getMessage());
        }
        return false;
    }

    // Métodos de control
    public void pause() {
        isPaused.set(true);
        updateStatus("Verificación pausada");
    }

    public void resume() {
        isPaused.set(false);
        updateStatus("Verificación reanudada");
    }

    public void cancel() {
        isCanceled.set(true);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Executor no terminó en el tiempo esperado");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        updateStatus("Verificación cancelada");
    }

    public void reset() {
        cancel();
        workingProxies.clear();
        processedProxies.clear();
        checkedCount.set(0);
        totalCount.set(0);
        isPaused.set(false);
        isCanceled.set(false);
        executor = Executors.newFixedThreadPool(threadCount);
        updateStatus("Sistema reiniciado");
    }

    private void updateStatus(String message) {
        if (onStatusUpdate != null) {
            onStatusUpdate.accept(message);
        }
        logger.info(message);
    }

    // Métodos de consulta
    public boolean isPaused() {
        return isPaused.get();
    }

    public boolean isCanceled() {
        return isCanceled.get();
    }

    public synchronized int getProxyCount() {
        return workingProxies.size();
    }

    public synchronized int getValidProxyCount() {
        return (int) workingProxies.stream().filter(ProxyInfo::isValid).count();
    }

    public int getCheckedCount() {
        return checkedCount.get();
    }

    public int getTotalCount() {
        return totalCount.get();
    }

    public double getProgress() {
        int total = totalCount.get();
        return total > 0 ? (double) checkedCount.get() / total : 0.0;
    }

    public synchronized List<ProxyInfo> getAllProxies() {
        return new ArrayList<>(workingProxies);
    }

    public synchronized List<ProxyInfo> getValidProxies() {
        return workingProxies.stream()
                .filter(ProxyInfo::isValid)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public synchronized List<ProxyInfo> getProxiesByType(ProxyInfo.ProxyType type) {
        return workingProxies.stream()
                .filter(proxy -> proxy.getType() == type && proxy.isValid())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public synchronized List<ProxyInfo> getFastestProxies(int limit) {
        return workingProxies.stream()
                .filter(ProxyInfo::isValid)
                .sorted(Comparator.comparingLong(ProxyInfo::getResponseTime))
                .limit(limit)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    // Métodos de exportación
    public void exportValidProxiesToFile(String filename) throws IOException {
        List<ProxyInfo> validProxies = getValidProxies();

        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("# Proxies válidos exportados el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("# Total de proxies válidos: " + validProxies.size() + "\n\n");

            for (ProxyInfo proxy : validProxies) {
                writer.write(proxy.toFileFormat() + "\n");
            }
        }

        updateStatus("✅ " + validProxies.size() + " proxies válidos exportados a: " + filename);
    }

    public void exportProxiesByType(String filename, ProxyInfo.ProxyType type) throws IOException {
        List<ProxyInfo> proxies = getProxiesByType(type);

        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("# Proxies " + type + " válidos exportados el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("# Total de proxies " + type + ": " + proxies.size() + "\n\n");

            for (ProxyInfo proxy : proxies) {
                writer.write(proxy.toFileFormat() + "\n");
            }
        }

        updateStatus("✅ " + proxies.size() + " proxies " + type + " exportados a: " + filename);
    }

    public String getStatistics() {
        int total = getProxyCount();
        int valid = getValidProxyCount();
        int invalid = total - valid;
        double validPercent = total > 0 ? (double) valid / total * 100 : 0;

        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DE PROXIES ===\n");
        stats.append("Total verificados: ").append(total).append("\n");
        stats.append("Válidos: ").append(valid).append(" (").append(String.format("%.1f%%", validPercent)).append(")\n");
        stats.append("Inválidos: ").append(invalid).append("\n");
        stats.append("Progreso: ").append(checkedCount.get()).append("/").append(totalCount.get()).append("\n");

        if (!workingProxies.isEmpty()) {
            Map<ProxyInfo.ProxyType, Long> typeCount = workingProxies.stream()
                    .filter(ProxyInfo::isValid)
                    .collect(Collectors.groupingBy(ProxyInfo::getType, Collectors.counting()));

            stats.append("\nPor tipo:\n");
            for (Map.Entry<ProxyInfo.ProxyType, Long> entry : typeCount.entrySet()) {
                stats.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }

            OptionalDouble avgResponseTime = workingProxies.stream()
                    .filter(ProxyInfo::isValid)
                    .mapToLong(ProxyInfo::getResponseTime)
                    .average();

            if (avgResponseTime.isPresent()) {
                stats.append("Tiempo promedio de respuesta: ").append(String.format("%.0f ms", avgResponseTime.getAsDouble())).append("\n");
            }
        }

        return stats.toString();
    }

    public void shutdown() {
        cancel();
    }

    public void printProxyStatus() {
        System.out.println("=== ESTADO DE PROXIES ===");
        System.out.println("Total verificados: " + getTotalCount());
        System.out.println("Proxies válidos: " + getValidProxyCount());
        System.out.println("Progreso: " + getCheckedCount() + "/" + getTotalCount() + " (" + String.format("%.1f%%", getProgress() * 100) + ")");
        System.out.println("Pausado: " + isPaused());
        System.out.println("Cancelado: " + isCanceled());
        System.out.println("Hilos activos: " + threadCount);
    }

    public void forceFullRevalidation() {
        updateStatus("🔄 Forzando revalidación completa de todos los proxies...");
        isCanceled.set(false);
        isPaused.set(false);
        checkedCount.set(0);
        totalCount.set(workingProxies.size());

        // Reiniciar el executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        executor = Executors.newFixedThreadPool(threadCount);

        // Revalidar todos los proxies
        checkProxies(new ArrayList<>(workingProxies));
    }
}
