package hudson.remoting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link JarCache} that stores files in a single directory.
 *
 * @author Kohsuke Kawaguchi
 * @since XXX prefetch-JENKINS-15120
 */
public class FileSystemJarCache extends JarCacheSupport {
    public final File rootDir;

    private final boolean touch;

    /**
     * We've reported these checksums as present on this side.
     */
    private final Set<Checksum> notified = Collections.synchronizedSet(new HashSet<Checksum>());

    /**
     * @param touch
     *      True to touch the cached jar file that's used. This enables external LRU based cache
     *      eviction at the expense of increased I/O.
     */
    public FileSystemJarCache(File rootDir, boolean touch) {
        this.rootDir = rootDir;
        this.touch = touch;
        if (rootDir==null)
            throw new IllegalArgumentException();
        rootDir.mkdirs();
    }

    @Override
    protected URL lookInCache(Channel channel, long sum1, long sum2) throws IOException, InterruptedException {
        File jar = map(sum1, sum2);
        if (jar.exists()) {
            LOGGER.log(Level.FINER, String.format("Jar file cache hit %16X%16X",sum1,sum2));
            if (touch)  jar.setLastModified(System.currentTimeMillis());
            if (notified.add(new Checksum(sum1,sum2)))
                getJarLoader(channel).notifyJarPresence(sum1,sum2);
            return jar.toURI().toURL();
        }
        return null;
    }

    @Override
    protected URL retrieve(Channel channel, long sum1, long sum2) throws IOException, InterruptedException {
        File target = map(sum1, sum2);
        File parent = target.getParentFile();
        parent.mkdirs();
        File tmp = File.createTempFile(target.getName(),"tmp", parent);
        try {
            RemoteOutputStream o = new RemoteOutputStream(new FileOutputStream(tmp));
            try {
                LOGGER.log(Level.FINE, String.format("Retrieving jar file %16X%16X",sum1,sum2));
                getJarLoader(channel).writeJarTo(sum1, sum2, o);
            } finally {
                o.close();
            }

            tmp.renameTo(target);

            if (target.exists()) {
                // even if we fail to rename, we are OK as long as the target actually exists at this point
                // this can happen if two FileSystejarCache instances share the same cache dir
                return target.toURI().toURL();
            }

            // for example if the file system went read only in the mean time
            throw new IOException("Unable to create "+target+" from "+tmp);
        } finally {
            tmp.delete();
        }
    }

    /**
     * Map to the cache jar file name.
     */
    File map(long sum1, long sum2) {
        return new File(rootDir,String.format("%02X/%014X%016X.jar",
                (int)(sum1>>>(64-8)),
                sum1&0x00FFFFFFFFFFFFFFL, sum2));
    }

    private static final Logger LOGGER = Logger.getLogger(FileSystemJarCache.class.getName());
}
