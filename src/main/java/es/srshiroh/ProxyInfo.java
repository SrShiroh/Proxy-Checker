package es.srshiroh;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Clase que representa la información de un proxy
 */
public class ProxyInfo {
    private final String host;
    private final int port;
    private final ProxyType type;
    private boolean isValid;
    private long responseTime;
    private String errorMessage;
    private LocalDateTime lastChecked;
    private String country;
    private boolean isAnonymous;

    public enum ProxyType {
        HTTP("HTTP"),
        HTTPS("HTTPS"),
        SOCKS4("SOCKS4"),
        SOCKS5("SOCKS5");

        private final String displayName;

        ProxyType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        public static ProxyType fromString(String type) {
            for (ProxyType proxyType : values()) {
                if (proxyType.displayName.equalsIgnoreCase(type)) {
                    return proxyType;
                }
            }
            return HTTP; // Por defecto
        }
    }

    public ProxyInfo(String host, int port, ProxyType type) {
        this.host = host;
        this.port = port;
        this.type = type;
        this.isValid = false;
        this.responseTime = -1;
        this.lastChecked = null;
        this.country = "Unknown";
        this.isAnonymous = false;
    }

    // Constructor desde string (formato: ip:puerto o ip:puerto:tipo)
    public static ProxyInfo fromString(String proxyString) {
        String[] parts = proxyString.trim().split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Formato de proxy inválido: " + proxyString);
        }

        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        ProxyType type = parts.length > 2 ? ProxyType.fromString(parts[2]) : ProxyType.HTTP;

        return new ProxyInfo(host, port, type);
    }

    // Getters
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public ProxyType getType() {
        return type;
    }

    public boolean isValid() {
        return isValid;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getLastChecked() {
        return lastChecked;
    }

    public String getCountry() {
        return country;
    }

    public boolean isAnonymous() {
        return isAnonymous;
    }

    // Setters
    public void setValid(boolean valid) {
        this.isValid = valid;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setLastChecked(LocalDateTime lastChecked) {
        this.lastChecked = lastChecked;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setAnonymous(boolean anonymous) {
        this.isAnonymous = anonymous;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    public String getFullAddress() {
        return type + "://" + host + ":" + port;
    }

    public String toFileFormat() {
        return host + ":" + port + ":" + type.displayName;
    }

    public String getStatusString() {
        if (!isValid) {
            return "❌ INVALID" + (errorMessage != null ? " (" + errorMessage + ")" : "");
        }
        return "✅ VALID (" + responseTime + "ms)";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getFullAddress());

        if (isValid) {
            sb.append(" [✅ ").append(responseTime).append("ms");
            if (country != null && !country.equals("Unknown")) {
                sb.append(", ").append(country);
            }
            if (isAnonymous) {
                sb.append(", Anonymous");
            }
            sb.append("]");
        } else {
            sb.append(" [❌");
            if (errorMessage != null) {
                sb.append(" ").append(errorMessage);
            }
            sb.append("]");
        }

        if (lastChecked != null) {
            sb.append(" (").append(lastChecked.format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append(")");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ProxyInfo proxyInfo = (ProxyInfo) obj;
        return port == proxyInfo.port &&
               host.equals(proxyInfo.host) &&
               type == proxyInfo.type;
    }

    @Override
    public int hashCode() {
        return host.hashCode() + port + type.hashCode();
    }
}
