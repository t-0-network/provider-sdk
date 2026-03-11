package network.t0.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides version information from embedded resources.
 */
public final class Version {

    private static final String VERSION_PROPERTIES = "/version.properties";
    private static final String DEFAULT_VERSION = "unknown";

    private static String cachedVersion;

    private Version() {
        // Utility class
    }

    /**
     * Gets the SDK/CLI version from embedded version.properties.
     *
     * @return the version string
     */
    public static String get() {
        if (cachedVersion != null) {
            return cachedVersion;
        }

        try (InputStream is = Version.class.getResourceAsStream(VERSION_PROPERTIES)) {
            if (is == null) {
                cachedVersion = DEFAULT_VERSION;
                return cachedVersion;
            }

            Properties props = new Properties();
            props.load(is);
            cachedVersion = props.getProperty("version", DEFAULT_VERSION);
            return cachedVersion;
        } catch (IOException e) {
            cachedVersion = DEFAULT_VERSION;
            return cachedVersion;
        }
    }
}
