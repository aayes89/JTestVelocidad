/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package netwatchdog;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 *
 * @author Slam
 */
public class NWDUI extends javax.swing.JFrame {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(NWDUI.class.getName());
    // Constantes
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 15000;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int LATENCY_TESTS = 5;
    private static final int ERROR_GENERAL = -1;
    private static final int ERROR_RATE_LIMIT = -2;

    // Controladores
    SpeedTestServer servidor = null;
    private NetworkLocation localizacion;
    private SwingWorker<Boolean, ProgressUpdate> worker;

    /**
     * Creates new form NWDUI
     */
    public NWDUI() {
        initComponents();
        // Cargar ubicación al iniciar
        obtenerUbicacionInicial();
    }

    private void log(String texto) {
        txtLog.append(texto + "\n");
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }

    private void obtenerUbicacionInicial() {
        SwingWorker<NetworkLocation, Void> worker = new SwingWorker<>() {
            @Override
            protected NetworkLocation doInBackground() throws Exception {
                log("Obteniendo ubicación de red...");
                return GeoLocator.locate();
            }

            @Override
            protected void done() {
                try {
                    jpbProceso.setValue(0);
                    localizacion = get();
                    jpbProceso.setValue(100);
                    jlbIP.setText("IP: " + localizacion.getIp());
                    jlbPais.setText("País: " + localizacion.getCountry());
                    jlbCiudad.setText("Ciudad: " + localizacion.getCity());
                    jlbLatLon.setText(String.format("Lat/Lon: %.4f, %.4f", localizacion.getLatitude(), localizacion.getLongitude()));
                    log("Ubicación obtenida correctamente.");
                } catch (InterruptedException | ExecutionException ex) {
                    log("ERROR GeoIP: " + ex.getMessage());
                    JOptionPane.showMessageDialog(rootPane, "Error obteniendo geolocalización.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void ejecutarPruebaEnSegundoPlano() {
        btnTestManual.setText("Cancelar");
        jpbProceso.setIndeterminate(false);
        jpbProceso.setValue(0);
        jpbProceso.setString("Iniciando...");
        log("\n--- INICIANDO TEST DE RED ---");

        worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                if (isCancelled()) {
                    return false;
                }

                publish(new ProgressUpdate(5, "Seleccionando servidor..."));
                servidor = seleccionarServidor();

                if (servidor == null || isCancelled()) {
                    if (servidor == null) {
                        publish(new ProgressUpdate(0, "CONEXIÓN CAÍDA: ningún servidor disponible."));
                        jpbProceso.setString("Prueba Fallida");
                    }
                    return false;
                }

                // Actualizar interfaz con datos del servidor
                SwingUtilities.invokeLater(() -> {
                    jlbNombre.setText("Nombre: " + servidor.getName());
                    jlbSponsor.setText("Sponsor: " + servidor.getSponsor());
                    jlbDist.setText(String.format("Distancia: %.2f km", servidor.getDistance()));
                });

                if (isCancelled()) {
                    return false;
                }

                publish(new ProgressUpdate(20, "Midiendo latencia..."));
                double[] latencias = medirLatencia(servidor);
                if (latencias == null || isCancelled()) {
                    if (latencias == null && !isCancelled()) {
                        publish(new ProgressUpdate(20, "ERROR: no se pudo medir latencia."));
                    }
                    return false;
                }

                publish(new ProgressUpdate(40, "Midiendo velocidad de descarga..."));
                double velocidad = medirDescarga(servidor, pct -> publish(new ProgressUpdate(pct, "Descargando datos...")));

                if (isCancelled()) {
                    return false;
                }

                if (velocidad == ERROR_RATE_LIMIT) {
                    publish(new ProgressUpdate(45, "Servidor limitado (HTTP 429). Buscando alternativo..."));
                    servidor = seleccionarServidorAlternativo(servidor);
                    if (servidor == null || isCancelled()) {
                        if (servidor == null) {
                            publish(new ProgressUpdate(45, "No hay servidores alternativos disponibles."));
                        }
                        return false;
                    }

                    latencias = medirLatencia(servidor);
                    if (latencias == null || isCancelled()) {
                        return false;
                    }

                    velocidad = medirDescarga(servidor, pct -> publish(new ProgressUpdate(pct, "Descargando datos...")));
                }

                if (isCancelled()) {
                    return false;
                }

                if (velocidad < 0) {
                    publish(new ProgressUpdate(50, "ERROR EN PRUEBA DE DESCARGA."));
                    jpbProceso.setString("Prueba Fallida");
                    return false;
                }

                publish(new ProgressUpdate(90, "Calculando métricas finales..."));

                final double latPromedio = latencias[1];
                final double velFinal = velocidad;
                final String estado = determinarEstado(velFinal, latPromedio);

                SwingUtilities.invokeLater(() -> {
                    jlbLatencia.setText(String.format("Latencia Media: %.2f ms", latPromedio));
                    jlbVelocidad.setText(String.format("Velocidad: %.2f Mbps", velFinal));
                    jlbEstado.setText("Estado: " + estado);

                    if (estado.contains("ESTABLE")) {
                        jlbEstado.setForeground(new Color(0, 128, 0));
                    } else if (estado.contains("LENTA") || estado.contains("CAÍDA")) {
                        jlbEstado.setForeground(Color.RED);
                    } else {
                        jlbEstado.setForeground(Color.ORANGE);
                    }
                });

                publish(new ProgressUpdate(100, String.format("Resultado final: %.2f Mbps | %.2f ms | Status: %s", velFinal, latPromedio, estado)));
                return true;
            }

            @Override
            protected void process(List<ProgressUpdate> chunks) {
                for (ProgressUpdate update : chunks) {
                    if (update.progress >= 0) {
                        jpbProceso.setValue(update.progress);
                        jpbProceso.setString(update.progress + "% - " + update.logMessage);
                    }
                    log(update.logMessage);
                }
            }

            @Override
            protected void done() {
                btnTestManual.setText("Iniciar Test Manual");
                if (isCancelled()) {
                    jpbProceso.setValue(0);
                    jpbProceso.setString("Cancelado");
                    log("PRUEBA CANCELADA POR EL USUARIO.");
                } else {
                    jpbProceso.setValue(100);
                    jpbProceso.setString("Prueba completada");
                }
            }
        };

        worker.execute();
    }

    private SpeedTestServer seleccionarServidor() {
        try {
            OoklaServerClient client = new OoklaServerClient();
            List<SpeedTestServer> servidores = client.obtenerServidores();

            if (servidores == null || servidores.isEmpty()) {
                return null;
            }

            servidores.sort((a, b) -> Double.compare(a.getDistance(), b.getDistance()));
            return servidores.get(0);
        } catch (IOException ex) {
            log("ERROR seleccionando servidor: " + ex.getMessage());
            return null;
        }
    }

    private SpeedTestServer seleccionarServidorAlternativo(SpeedTestServer excluido) {
        try {
            OoklaServerClient client = new OoklaServerClient();
            List<SpeedTestServer> servidores = client.obtenerServidores();
            servidores.sort((a, b) -> Double.compare(a.getDistance(), b.getDistance()));

            for (SpeedTestServer server : servidores) {
                if (server.getId() != excluido.getId()) {
                    return server;
                }
            }
        } catch (IOException ex) {
            log("ERROR buscando servidor alternativo: " + ex.getMessage());
        }
        return null;
    }

    private double[] medirLatencia(SpeedTestServer servidor) {
        double min = Double.MAX_VALUE;
        double max = 0;
        double suma = 0;
        int exitosas = 0;

        String[] esquemas = {"http://", "https://"};
        String[] endpoints = {"/latency", "/"};

        for (int i = 0; i < LATENCY_TESTS; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            boolean exito = false;

            for (String esquema : esquemas) {
                if (exito || Thread.currentThread().isInterrupted()) {
                    break;
                }
                for (String endpoint : endpoints) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    HttpURLConnection conn = null;
                    try {
                        String targetUrl = esquema + servidor.getHost() + endpoint;
                        URL url = new URL(targetUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(CONNECT_TIMEOUT);
                        conn.setReadTimeout(READ_TIMEOUT);
                        conn.setRequestMethod(endpoint.equals("/") ? "HEAD" : "GET");
                        conn.setUseCaches(false);

                        long inicio = System.nanoTime();
                        int responseCode = conn.getResponseCode();

                        if (responseCode == 200 || responseCode == 204) {
                            if (!endpoint.equals("/")) {
                                try (InputStream in = conn.getInputStream()) {
                                    byte[] buffer = new byte[1024];
                                    while (in.read(buffer) != -1) {
                                        if (Thread.currentThread().isInterrupted()) {
                                            return null;
                                        }
                                    }
                                }
                            }
                            long fin = System.nanoTime();
                            double ms = (fin - inicio) / 1_000_000.0;

                            min = Math.min(min, ms);
                            max = Math.max(max, ms);
                            suma += ms;
                            exitosas++;
                            exito = true;
                            break;
                        }
                    } catch (IOException ex) {
                        // Reintento
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }
                }
            }
        }

        if (exitosas == 0 || Thread.currentThread().isInterrupted()) {
            return null;
        }
        return new double[]{min, suma / exitosas, max};
    }

    private double medirDescarga(SpeedTestServer servidor, Consumer<Integer> progressConsumer) {
        HttpURLConnection conn = null;
        try {
            String downloadURL = "http://" + servidor.getHost() + "/download?size=10000000";
            URL url = new URL(downloadURL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);

            int responseCode = conn.getResponseCode();
            if (responseCode == 429) {
                return ERROR_RATE_LIMIT;
            }
            if (responseCode != 200) {
                return ERROR_GENERAL;
            }

            long totalBytesEsperados = conn.getContentLengthLong();
            if (totalBytesEsperados <= 0) {
                totalBytesEsperados = 10_000_000; // 10MB fallback
            }
            byte[] buffer = new byte[BUFFER_SIZE];
            long bytesLeidosTotal = 0;

            long inicio = System.nanoTime();
            try (InputStream in = conn.getInputStream()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        return ERROR_GENERAL;
                    }

                    bytesLeidosTotal += read;

                    // Mapear el avance del stream (0 a 100%) al rango 40% a 85% de la JProgressBar
                    if (progressConsumer != null) {
                        int pctDescarga = (int) ((bytesLeidosTotal * 100) / totalBytesEsperados);
                        int pctGlobal = 40 + (int) (pctDescarga * 0.45);
                        progressConsumer.accept(pctGlobal);
                    }
                }
            }
            long fin = System.nanoTime();

            double segundos = (fin - inicio) / 1_000_000_000.0;
            if (segundos <= 0) {
                return ERROR_GENERAL;
            }

            double megabits = (bytesLeidosTotal * 8.0) / 1_000_000.0;
            return megabits / segundos;
        } catch (IOException ex) {
            return ERROR_GENERAL;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String determinarEstado(double velocidad, double latencia) {
        if (velocidad < 10) {
            return "CONEXIÓN LENTA";
        }
        if (velocidad < 25) {
            return "CONEXIÓN DEGRADADA";
        }
        if (latencia > 100) {
            return "LATENCIA ALTA";
        }
        if (latencia > 50) {
            return "LATENCIA ELEVADA";
        }
        return "CONEXIÓN ESTABLE";
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane2 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        jlbIP = new javax.swing.JLabel();
        jlbCiudad = new javax.swing.JLabel();
        jlbPais = new javax.swing.JLabel();
        jlbLatLon = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        jPanel2 = new javax.swing.JPanel();
        jlbNombre = new javax.swing.JLabel();
        jlbSponsor = new javax.swing.JLabel();
        jlbDist = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jlbLatencia = new javax.swing.JLabel();
        jlbVelocidad = new javax.swing.JLabel();
        jlbEstado = new javax.swing.JLabel();
        btnTestManual = new javax.swing.JButton();
        jpbProceso = new javax.swing.JProgressBar();
        jScrollPane3 = new javax.swing.JScrollPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtLog = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Network SpeedTest - WatchDog by (Slam 2026)");
        setLocationByPlatform(true);
        setResizable(false);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Ubicación local", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Helvetica Neue", 1, 13))); // NOI18N

        jlbIP.setText("IP:");

        jlbCiudad.setText("Ciudad: ");

        jlbPais.setText("País: ");

        jlbLatLon.setText("Lat/lon:");

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jlbIP, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jlbCiudad, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jlbPais, javax.swing.GroupLayout.PREFERRED_SIZE, 224, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jlbLatLon, javax.swing.GroupLayout.PREFERRED_SIZE, 224, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jSeparator1)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jlbIP)
                            .addComponent(jlbPais))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jlbCiudad)
                            .addComponent(jlbLatLon, javax.swing.GroupLayout.Alignment.TRAILING))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Servidor seleccionado", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Helvetica Neue", 1, 13))); // NOI18N

        jlbNombre.setText("Nombre:");

        jlbSponsor.setText("Sponsor:");

        jlbDist.setText("Distancia:");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jlbNombre)
                    .addComponent(jlbSponsor)
                    .addComponent(jlbDist))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jlbNombre)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jlbSponsor)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jlbDist)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Métricas de conexión", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Helvetica Neue", 1, 13))); // NOI18N

        jlbLatencia.setText("Latencia media:");

        jlbVelocidad.setText("Velocidad:");

        jlbEstado.setText("Estado:");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jlbLatencia)
                    .addComponent(jlbVelocidad)
                    .addComponent(jlbEstado))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jlbLatencia)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jlbVelocidad)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jlbEstado)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        btnTestManual.setText("Iniciar Test Manual");
        btnTestManual.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnTestManual.addActionListener(this::btnTestManualActionPerformed);

        jpbProceso.setToolTipText("");
        jpbProceso.setIndeterminate(true);
        jpbProceso.setStringPainted(true);

        jScrollPane3.setBackground(new java.awt.Color(255, 255, 255));
        jScrollPane3.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Consola de eventos", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Helvetica Neue", 1, 13))); // NOI18N

        txtLog.setEditable(false);
        txtLog.setBackground(new java.awt.Color(255, 255, 255));
        txtLog.setColumns(20);
        txtLog.setFont(new java.awt.Font("Monospaced", 0, 11)); // NOI18N
        txtLog.setRows(5);
        jScrollPane1.setViewportView(txtLog);

        jScrollPane3.setViewportView(jScrollPane1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnTestManual, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jpbProceso, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane3))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnTestManual)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jpbProceso, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnTestManualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTestManualActionPerformed
        // TODO add your handling code here:
        if (worker != null && !worker.isDone()) {
            log("CANCELANDO PRUEBA...");
            worker.cancel(true);
        } else {
            ejecutarPruebaEnSegundoPlano();
        }
    }//GEN-LAST:event_btnTestManualActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new NWDUI().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnTestManual;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JLabel jlbCiudad;
    private javax.swing.JLabel jlbDist;
    private javax.swing.JLabel jlbEstado;
    private javax.swing.JLabel jlbIP;
    private javax.swing.JLabel jlbLatLon;
    private javax.swing.JLabel jlbLatencia;
    private javax.swing.JLabel jlbNombre;
    private javax.swing.JLabel jlbPais;
    private javax.swing.JLabel jlbSponsor;
    private javax.swing.JLabel jlbVelocidad;
    private javax.swing.JProgressBar jpbProceso;
    private javax.swing.JTextArea txtLog;
    // End of variables declaration//GEN-END:variables

    private static class ProgressUpdate {

        final int progress;
        final String logMessage;

        ProgressUpdate(int progress, String logMessage) {
            this.progress = progress;
            this.logMessage = logMessage;
        }
    }
}
