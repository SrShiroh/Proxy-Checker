package es.srshiroh;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Interfaz gráfica principal para el ProxyChecker
 */
public class ProxyCheckerGUI extends JFrame {
    private ProxyManager proxyManager;

    // Componentes principales
    private JTable proxyTable;
    private DefaultTableModel tableModel;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel statsLabel;

    // Controles
    private JButton loadButton;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton resetButton;
    private JButton exportButton;
    private JButton exportByTypeButton;

    // Configuración
    private JSpinner timeoutSpinner;
    private JSpinner threadsSpinner;
    private JTextField testUrlField;
    private JCheckBox httpsCheckBox;

    // Filtros
    private JComboBox<String> typeFilter;
    private JCheckBox validOnlyCheckBox;

    public ProxyCheckerGUI() {
        initializeComponents();
        setupLayout();
        setupEventListeners();
        initializeProxyManager();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("ProxyChecker v1.0 - by SrShiroh");
        setSize(1000, 700);
        setLocationRelativeTo(null);
    }

    private void initializeComponents() {
        // Tabla de proxies
        String[] columnNames = {"Host", "Puerto", "Tipo", "Estado", "Tiempo (ms)", "País", "Última verificación"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        proxyTable = new JTable(tableModel);
        proxyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Barra de progreso
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");

        // Labels de estado y estadísticas
        statusLabel = new JLabel("Listo para cargar proxies");
        statsLabel = new JLabel("Total: 0 | Válidos: 0 | Inválidos: 0");

        // Botones de control
        loadButton = new JButton("Cargar Proxies");
        pauseButton = new JButton("Pausar");
        stopButton = new JButton("Detener");
        resetButton = new JButton("Reiniciar");
        exportButton = new JButton("Exportar Válidos");
        exportByTypeButton = new JButton("Exportar por Tipo");

        // Configuración
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(10000, 1000, 60000, 1000));
        threadsSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 200, 5));
        testUrlField = new JTextField("http://httpbin.org/ip");
        httpsCheckBox = new JCheckBox("Verificar HTTPS", true);

        // Filtros
        typeFilter = new JComboBox<>(new String[]{"Todos", "HTTP", "HTTPS", "SOCKS4", "SOCKS5"});
        validOnlyCheckBox = new JCheckBox("Solo válidos");

        // Estados iniciales
        pauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        exportButton.setEnabled(false);
        exportByTypeButton.setEnabled(false);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Panel superior - Configuración
        JPanel configPanel = createConfigPanel();
        add(configPanel, BorderLayout.NORTH);

        // Panel central - Tabla de proxies
        JScrollPane scrollPane = new JScrollPane(proxyTable);
        scrollPane.setBorder(new TitledBorder("Proxies"));
        add(scrollPane, BorderLayout.CENTER);

        // Panel inferior - Estado y controles
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Configuración"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Fila 1: Timeout y Threads
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Timeout (ms):"), gbc);
        gbc.gridx = 1;
        panel.add(timeoutSpinner, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("Hilos:"), gbc);
        gbc.gridx = 3;
        panel.add(threadsSpinner, gbc);

        // Fila 2: URL de prueba y HTTPS
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("URL de prueba:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(testUrlField, gbc);
        gbc.gridx = 3; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(httpsCheckBox, gbc);

        // Fila 3: Filtros
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Filtro tipo:"), gbc);
        gbc.gridx = 1;
        panel.add(typeFilter, gbc);
        gbc.gridx = 2;
        panel.add(validOnlyCheckBox, gbc);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Panel de controles
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.add(loadButton);
        controlPanel.add(pauseButton);
        controlPanel.add(stopButton);
        controlPanel.add(resetButton);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(exportButton);
        controlPanel.add(exportByTypeButton);

        // Panel de estado
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(progressBar, BorderLayout.NORTH);
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(statsLabel, BorderLayout.SOUTH);

        panel.add(controlPanel, BorderLayout.NORTH);
        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void setupEventListeners() {
        loadButton.addActionListener(e -> loadProxies());
        pauseButton.addActionListener(e -> pauseVerification());
        stopButton.addActionListener(e -> stopVerification());
        resetButton.addActionListener(e -> resetVerification());
        exportButton.addActionListener(e -> exportValidProxies());
        exportByTypeButton.addActionListener(e -> exportProxiesByType());

        // Listeners de configuración
        timeoutSpinner.addChangeListener(e -> updateConfiguration());
        threadsSpinner.addChangeListener(e -> updateConfiguration());
        testUrlField.addActionListener(e -> updateConfiguration());
        httpsCheckBox.addActionListener(e -> updateConfiguration());

        // Listeners de filtros
        typeFilter.addActionListener(e -> applyFilters());
        validOnlyCheckBox.addActionListener(e -> applyFilters());
    }

    private void initializeProxyManager() {
        proxyManager = new ProxyManager();

        // Configurar callbacks
        proxyManager.setOnProxyChecked(this::onProxyChecked);
        proxyManager.setOnStatusUpdate(this::onStatusUpdate);
        proxyManager.setOnCompleted(this::onVerificationCompleted);

        updateConfiguration();
    }

    private void loadProxies() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Seleccionar archivo de proxies");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            SwingUtilities.invokeLater(() -> {
                try {
                    tableModel.setRowCount(0);
                    proxyManager.reset();

                    loadButton.setEnabled(false);
                    pauseButton.setEnabled(true);
                    stopButton.setEnabled(true);
                    exportButton.setEnabled(false);
                    exportByTypeButton.setEnabled(false);

                    proxyManager.loadProxiesFromFile(selectedFile.getAbsolutePath());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this,
                        "Error cargando archivo: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                    onVerificationCompleted();
                }
            });
        }
    }

    private void pauseVerification() {
        if (proxyManager.isPaused()) {
            proxyManager.resume();
            pauseButton.setText("Pausar");
        } else {
            proxyManager.pause();
            pauseButton.setText("Reanudar");
        }
    }

    private void stopVerification() {
        proxyManager.cancel();
        onVerificationCompleted();
    }

    private void resetVerification() {
        proxyManager.reset();
        tableModel.setRowCount(0);
        progressBar.setValue(0);
        progressBar.setString("0%");
        updateStats();

        loadButton.setEnabled(true);
        pauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        exportButton.setEnabled(false);
        exportByTypeButton.setEnabled(false);
        pauseButton.setText("Pausar");
    }

    private void exportValidProxies() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar proxies válidos");
        fileChooser.setSelectedFile(new File("valid_proxies.txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                proxyManager.exportValidProxiesToFile(fileChooser.getSelectedFile().getAbsolutePath());
                JOptionPane.showMessageDialog(this, "Proxies válidos exportados correctamente",
                    "Éxito", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error exportando: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportProxiesByType() {
        String[] types = {"HTTP", "HTTPS", "SOCKS4", "SOCKS5"};
        String selectedType = (String) JOptionPane.showInputDialog(this,
            "Seleccionar tipo de proxy a exportar:", "Exportar por tipo",
            JOptionPane.QUESTION_MESSAGE, null, types, types[0]);

        if (selectedType != null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Guardar proxies " + selectedType);
            fileChooser.setSelectedFile(new File(selectedType.toLowerCase() + "_proxies.txt"));

            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    ProxyInfo.ProxyType type = ProxyInfo.ProxyType.fromString(selectedType);
                    proxyManager.exportProxiesByType(fileChooser.getSelectedFile().getAbsolutePath(), type);
                    JOptionPane.showMessageDialog(this, "Proxies " + selectedType + " exportados correctamente",
                        "Éxito", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error exportando: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void updateConfiguration() {
        if (proxyManager != null) {
            proxyManager.setTimeout((Integer) timeoutSpinner.getValue());
            proxyManager.setThreadCount((Integer) threadsSpinner.getValue());
            proxyManager.setTestUrl(testUrlField.getText());
            proxyManager.setTestHttps(httpsCheckBox.isSelected());
        }
    }

    private void applyFilters() {
        // Implementar filtros en la tabla
        // Por simplicidad, se podría implementar un TableRowSorter
        // Para este ejemplo, solo actualizamos la vista
        updateTableDisplay();
    }

    private void updateTableDisplay() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            List<ProxyInfo> proxies = proxyManager.getAllProxies();

            String selectedType = (String) typeFilter.getSelectedItem();
            boolean validOnly = validOnlyCheckBox.isSelected();

            for (ProxyInfo proxy : proxies) {
                // Aplicar filtros
                if (validOnly && !proxy.isValid()) {
                    continue;
                }

                if (!"Todos".equals(selectedType) && !proxy.getType().toString().equals(selectedType)) {
                    continue;
                }

                addProxyToTable(proxy);
            }
        });
    }

    private void addProxyToTable(ProxyInfo proxy) {
        Object[] row = {
            proxy.getHost(),
            proxy.getPort(),
            proxy.getType(),
            proxy.getStatusString(),
            proxy.getResponseTime() > 0 ? proxy.getResponseTime() : "-",
            proxy.getCountry(),
            proxy.getLastChecked() != null ?
                proxy.getLastChecked().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "-"
        };
        tableModel.addRow(row);
    }

    private void onProxyChecked(ProxyInfo proxy) {
        SwingUtilities.invokeLater(() -> {
            // Aplicar filtros antes de añadir
            String selectedType = (String) typeFilter.getSelectedItem();
            boolean validOnly = validOnlyCheckBox.isSelected();

            if (validOnly && !proxy.isValid()) {
                return;
            }

            if (!"Todos".equals(selectedType) && !proxy.getType().toString().equals(selectedType)) {
                return;
            }

            addProxyToTable(proxy);
            updateProgress();
            updateStats();
        });
    }

    private void onStatusUpdate(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    private void onVerificationCompleted() {
        SwingUtilities.invokeLater(() -> {
            loadButton.setEnabled(true);
            pauseButton.setEnabled(false);
            stopButton.setEnabled(false);
            exportButton.setEnabled(true);
            exportByTypeButton.setEnabled(true);
            pauseButton.setText("Pausar");

            progressBar.setValue(100);
            progressBar.setString("Completado");

            updateStats();

            // Mostrar estadísticas finales
            String stats = proxyManager.getStatistics();
            JOptionPane.showMessageDialog(this, stats, "Verificación Completada",
                JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void updateProgress() {
        double progress = proxyManager.getProgress();
        int progressPercent = (int) (progress * 100);
        progressBar.setValue(progressPercent);
        progressBar.setString(progressPercent + "%");
    }

    private void updateStats() {
        int total = proxyManager.getProxyCount();
        int valid = proxyManager.getValidProxyCount();
        int invalid = total - valid;

        String statsText = String.format("Total: %d | Válidos: %d | Inválidos: %d",
            total, valid, invalid);
        statsLabel.setText(statsText);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Usar look and feel por defecto
        }

        SwingUtilities.invokeLater(() -> {
            new ProxyCheckerGUI().setVisible(true);
        });
    }
}
