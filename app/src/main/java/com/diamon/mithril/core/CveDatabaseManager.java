package com.diamon.mithril.core;

import android.content.Context;
import android.util.Log;

import com.diamon.mithril.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

public class CveDatabaseManager {
    private static final String TAG = "CveDatabaseManager";
    public static final String DEFAULT_BASE_URL = "https://github.com/nmatt0/mithril/releases/download/db-latest";

    private static final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private static volatile boolean cancelRequested = false;

    public interface DownloadCallback {
        void onLog(String line);
        void onProgress(int progressPercent, String status);
        void onFinished(boolean success, String message);
    }

    private static class AssetDef {
        final String servedName;
        final String targetName;
        final boolean isGzip;

        AssetDef(String servedName, String targetName, boolean isGzip) {
            this.servedName = servedName;
            this.targetName = targetName;
            this.isGzip = isGzip;
        }
    }

    public static File getDatabaseDir(Context context) {
        return new File(context.getFilesDir(), "mithril_db");
    }

    public static boolean isDownloading() {
        return isDownloading.get();
    }

    public static void cancelDownload() {
        cancelRequested = true;
    }

    public static boolean isDatabaseInstalled(Context context) {
        File dbDir = getDatabaseDir(context);
        if (!dbDir.exists() || !dbDir.isDirectory()) return false;

        File osv = new File(dbDir, "osv-index.mdb");
        File nvd = new File(dbDir, "nvd-index.json");
        return (osv.exists() && osv.length() > 0) || (nvd.exists() && nvd.length() > 0);
    }

    public static long getDatabaseTotalSize(Context context) {
        File dbDir = getDatabaseDir(context);
        if (!dbDir.exists() || !dbDir.isDirectory()) return 0;

        File[] files = dbDir.listFiles();
        if (files == null) return 0;

        long total = 0;
        for (File f : files) {
            if (f.isFile() && !f.getName().startsWith(".")) {
                total += f.length();
            }
        }
        return total;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static void startDownload(Context context, DownloadCallback callback) {
        if (!isDownloading.compareAndSet(false, true)) {
            if (callback != null) {
                callback.onFinished(false, context.getString(R.string.cve_db_download_in_progress));
            }
            return;
        }

        cancelRequested = false;

        new Thread(() -> {
            File dbDir = getDatabaseDir(context);
            File tempDir = new File(dbDir, ".download_tmp");
            boolean success = false;
            String resultMsg = "";

            try {
                if (!dbDir.exists() && !dbDir.mkdirs()) {
                    throw new IOException("Cannot create database directory: " + dbDir.getAbsolutePath());
                }

                deleteRecursively(tempDir);
                if (!tempDir.mkdirs()) {
                    throw new IOException("Cannot create temporary download directory: " + tempDir.getAbsolutePath());
                }

                String baseUrl = DEFAULT_BASE_URL;
                postLog(callback, context.getString(R.string.cve_db_log_start, baseUrl));

                // 1. Descargar manifiesto SHA256SUMS
                postLog(callback, context.getString(R.string.cve_db_log_manifest));
                String manifestUrl = baseUrl + "/SHA256SUMS";
                String manifestContent = downloadString(manifestUrl);

                if (cancelRequested) throw new InterruptedException("Download cancelled");

                Map<String, String> sums = parseSha256Sums(manifestContent);
                if (sums.isEmpty()) {
                    throw new IOException("SHA256SUMS manifest is empty or could not be parsed.");
                }

                // 2. Definir lista de activos a descargar
                AssetDef[] assets = new AssetDef[] {
                        new AssetDef("osv-index.mdb.gz", "osv-index.mdb", true),
                        new AssetDef("nvd-index.json", "nvd-index.json", false),
                        new AssetDef("kev.json", "kev.json", false),
                        new AssetDef("epss.txt.gz", "epss.txt", true)
                };

                // Si en el manifiesto existe el feed de kernel, incluirlo
                if (sums.containsKey("kernel-cve-index.json.gz")) {
                    AssetDef[] expanded = new AssetDef[assets.length + 1];
                    System.arraycopy(assets, 0, expanded, 0, assets.length);
                    expanded[assets.length] = new AssetDef("kernel-cve-index.json.gz", "kernel-cve-index.json", true);
                    assets = expanded;
                } else if (sums.containsKey("kernel-cve-index.json")) {
                    AssetDef[] expanded = new AssetDef[assets.length + 1];
                    System.arraycopy(assets, 0, expanded, 0, assets.length);
                    expanded[assets.length] = new AssetDef("kernel-cve-index.json", "kernel-cve-index.json", false);
                    assets = expanded;
                }

                for (int i = 0; i < assets.length; i++) {
                    if (cancelRequested) throw new InterruptedException("Download cancelled");

                    AssetDef asset = assets[i];
                    String expectedSha = sums.get(asset.servedName);
                    if (expectedSha == null) {
                        throw new IOException("Asset " + asset.servedName + " not found in SHA256SUMS manifest");
                    }

                    File downloadedFile = new File(tempDir, asset.servedName);
                    String assetUrl = baseUrl + "/" + asset.servedName;

                    int assetIndex = i + 1;
                    int totalAssets = assets.length;
                    postLog(callback, context.getString(R.string.cve_db_log_downloading, asset.servedName, assetIndex + "/" + totalAssets));

                    downloadFileWithVerification(assetUrl, downloadedFile, expectedSha, callback, assetIndex, totalAssets);

                    if (cancelRequested) throw new InterruptedException("Download cancelled");

                    postLog(callback, context.getString(R.string.cve_db_log_verifying, asset.servedName));

                    // 3. Descomprimir o mover archivo temporal preparado (.new)
                    File destStaged = new File(dbDir, asset.targetName + ".new");
                    if (destStaged.exists()) destStaged.delete();

                    if (asset.isGzip) {
                        postLog(callback, context.getString(R.string.cve_db_log_decompressing, asset.targetName));
                        inflateGzip(downloadedFile, destStaged);
                        downloadedFile.delete();
                    } else {
                        if (!downloadedFile.renameTo(destStaged)) {
                            copyFile(downloadedFile, destStaged);
                            downloadedFile.delete();
                        }
                    }
                }

                if (cancelRequested) throw new InterruptedException("Download cancelled");

                // 4. Reemplazo atomico final de los archivos
                for (AssetDef asset : assets) {
                    File staged = new File(dbDir, asset.targetName + ".new");
                    File finalFile = new File(dbDir, asset.targetName);
                    if (finalFile.exists()) {
                        finalFile.delete();
                    }
                    if (!staged.renameTo(finalFile)) {
                        copyFile(staged, finalFile);
                        staged.delete();
                    }
                }

                long totalInstalledSize = getDatabaseTotalSize(context);
                String formattedSize = formatSize(totalInstalledSize);
                resultMsg = context.getString(R.string.cve_db_log_success, formattedSize);
                postLog(callback, resultMsg);

                success = true;

            } catch (InterruptedException e) {
                resultMsg = context.getString(R.string.cve_db_log_cancelled);
                postLog(callback, resultMsg);
                success = false;
            } catch (Exception e) {
                Log.e(TAG, "Error downloading CVE database: " + e.getMessage(), e);
                resultMsg = context.getString(R.string.cve_db_log_error, e.getMessage());
                postLog(callback, resultMsg);
                success = false;
            } finally {
                // Limpiar temporales
                deleteRecursively(tempDir);
                // Limpiar .new residuales si fallo
                if (!success) {
                    File[] news = dbDir.listFiles((dir, name) -> name.endsWith(".new"));
                    if (news != null) {
                        for (File nf : news) nf.delete();
                    }
                }
                isDownloading.set(false);
                if (callback != null) {
                    callback.onFinished(success, resultMsg);
                }
            }
        }).start();
    }

    private static void postLog(DownloadCallback callback, String msg) {
        if (callback != null && msg != null) {
            callback.onLog(msg);
        }
    }

    private static HttpURLConnection openWithRedirects(String urlStr) throws IOException {
        int redirects = 0;
        while (redirects < 8) {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mithril-Android-Downloader/1.0");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308) {
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                if (newUrl == null) {
                    throw new IOException("HTTP redirect without Location header");
                }
                urlStr = newUrl;
                redirects++;
            } else if (code >= 200 && code < 300) {
                return conn;
            } else {
                conn.disconnect();
                throw new IOException("HTTP error " + code + " for URL: " + urlStr);
            }
        }
        throw new IOException("Too many redirects: " + urlStr);
    }

    private static String downloadString(String urlStr) throws IOException {
        HttpURLConnection conn = openWithRedirects(urlStr);
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[4096];
            int r;
            StringBuilder sb = new StringBuilder();
            while ((r = in.read(buf)) != -1) {
                if (cancelRequested) throw new IOException("Download cancelled");
                sb.append(new String(buf, 0, r, "UTF-8"));
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static Map<String, String> parseSha256Sums(String body) {
        Map<String, String> m = new HashMap<>();
        String[] lines = body.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.length() < 66) continue;
            String hash = line.substring(0, 64).toLowerCase(Locale.US);
            String name = line.substring(64).trim();
            if (name.startsWith("*")) {
                name = name.substring(1).trim();
            }
            if (!name.isEmpty()) {
                m.put(name, hash);
            }
        }
        return m;
    }

    private static void downloadFileWithVerification(String urlStr, File destFile, String expectedSha,
                                                    DownloadCallback callback, int fileIndex, int totalFiles) throws Exception {
        HttpURLConnection conn = openWithRedirects(urlStr);
        long contentLength = conn.getContentLengthLong();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = conn.getInputStream();
             OutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[32768];
            long totalRead = 0;
            int read;
            long lastReportTime = 0;

            while ((read = in.read(buffer)) != -1) {
                if (cancelRequested) {
                    throw new InterruptedException("Download cancelled");
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                totalRead += read;

                long now = System.currentTimeMillis();
                if (now - lastReportTime > 400 && callback != null) {
                    lastReportTime = now;
                    int percent = contentLength > 0 ? (int) ((totalRead * 100) / contentLength) : -1;
                    callback.onProgress(percent, String.format(Locale.US, "File %d/%d: %s (%s)",
                            fileIndex, totalFiles, destFile.getName(), formatSize(totalRead)));
                }
            }
            out.flush();
        } finally {
            conn.disconnect();
        }

        byte[] hashBytes = digest.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        String calculatedSha = hex.toString();

        if (!calculatedSha.equalsIgnoreCase(expectedSha)) {
            destFile.delete();
            throw new IOException("Checksum mismatch for " + destFile.getName() +
                    " (expected " + expectedSha + ", got " + calculatedSha + ")");
        }
    }

    private static void inflateGzip(File gzippedSource, File dest) throws IOException {
        try (InputStream fis = new FileInputStream(gzippedSource);
             GZIPInputStream gzis = new GZIPInputStream(fis, 32768);
             OutputStream fos = new FileOutputStream(dest)) {

            byte[] buffer = new byte[32768];
            int read;
            while ((read = gzis.read(buffer)) != -1) {
                if (cancelRequested) throw new IOException("Decompression cancelled");
                fos.write(buffer, 0, read);
            }
            fos.flush();
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[32768];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] subs = file.listFiles();
            if (subs != null) {
                for (File f : subs) deleteRecursively(f);
            }
        }
        file.delete();
    }
}
