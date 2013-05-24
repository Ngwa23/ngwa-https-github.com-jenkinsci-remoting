package hudson.remoting;

import hudson.remoting.Channel.Mode;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

/**
 * Represents additional features implemented on {@link Channel}.
 *
 * <p>
 * Each {@link Channel} exposes its capability to {@link Channel#getProperty(Object)}.
 *
 * <p>
 * This mechanism allows two different versions of <tt>remoting.jar</tt> to talk to each other.
 *
 * @author Kohsuke Kawaguchi
 * @see Channel#remoteCapability
 */
public final class Capability implements Serializable {
    /**
     * Bit mask of optional capabilities.
     */
    private final long mask;

    Capability(long mask) {
        this.mask = mask;
    }

    public Capability() {
        this(MASK_MULTI_CLASSLOADER|MASK_PIPE_THROTTLING|MASK_MIMIC_EXCEPTION|MASK_PREFETCH);
    }

    /**
     * Does this implementation supports multi-classloader serialization in
     * {@link UserRequest}?
     *
     * @see MultiClassLoaderSerializer
     */
    public boolean supportsMultiClassLoaderRPC() {
        return (mask&MASK_MULTI_CLASSLOADER)!=0;
    }

    /**
     * Does the implementation supports window size control over pipes?
     *
     * @see ProxyOutputStream
     */
    public boolean supportsPipeThrottling() {
        return (mask& MASK_PIPE_THROTTLING)!=0;
    }

    public boolean hasMimicException() {
        return (mask&MASK_MIMIC_EXCEPTION)!=0;
    }

    /**
     * Does the implementation allow classes to be prefetched and JARs to be cached?
     * @since XXX prefetch-JENKINS-15120
     */
    public boolean supportsPrefetch() {
        return (mask & MASK_PREFETCH) != 0;
    }

    /**
     * Writes out the capacity preamble.
     */
    void writePreamble(OutputStream os) throws IOException {
        os.write(PREAMBLE);
        ObjectOutputStream oos = new ObjectOutputStream(Mode.TEXT.wrap(os));
        oos.writeObject(this);
        oos.flush();
    }

    /**
     * The opposite operation of {@link #writePreamble(OutputStream)}.
     */
    public static Capability read(InputStream is) throws IOException {
        try {
            ObjectInputStream ois = new ObjectInputStream(Mode.TEXT.wrap(is));
            return (Capability)ois.readObject();
        } catch (ClassNotFoundException e) {
            throw (Error)new NoClassDefFoundError(e.getMessage()).initCause(e);
        }
    }

    private static final long serialVersionUID = 1L;

    /**
     * This was used briefly to indicate the use of {@link MultiClassLoaderSerializer}, but
     * that was disabled (see HUDSON-4293) in Sep 2009. AFAIK no released version of Hudson
     * exposed it, but since then the wire format of {@link MultiClassLoaderSerializer} has evolved
     * in an incompatible way.
     * <p>
     * So just to be on the safe side, I assigned a different bit to indicate this feature {@link #MASK_MULTI_CLASSLOADER},
     * so that even if there are remoting.jar out there that advertizes this bit, we won't be using
     * the new {@link MultiClassLoaderSerializer} code.
     * <p>
     * If we ever use up all 64bits of long, we can probably come back and reuse this bit, as by then
     * hopefully any such remoting.jar deployment is long gone. 
     */
    @SuppressWarnings("PointlessBitwiseExpression")
    private static final long MASK_UNUSED1 = 1L << 0;
    
    /**
     * Bit that indicates the use of {@link MultiClassLoaderSerializer}.
     */
    private static final long MASK_MULTI_CLASSLOADER = 1L << 1;

    /**
     * Bit that indicates the use of TCP-like window control for {@link ProxyOutputStream}.
     */
    private static final long MASK_PIPE_THROTTLING = 1L << 2;

    /**
     * Supports {@link MimicException}.
     */
    private static final long MASK_MIMIC_EXCEPTION = 1L << 3;

    /**
     * This flag indicates the support for advanced classloading features.
     *
     * <p>
     * This mainly involves two things:
     *
     * <ul>
     * <li>Prefetching, where a request to retrieve a class also reports where
     *     related classes can be found and loaded, which saves roundtrips.
     * <li>Caching, where we separate "which classloader should load a class" from
     *     "which jar file should load a class", enabling caching at the jar files level.
     * </ul>
     *
     * @see ResourceImageRef
     */
    private static final long MASK_PREFETCH = 1L << 4;

    static final byte[] PREAMBLE;

    public static final Capability NONE = new Capability(0);

    static {
        try {
            PREAMBLE = "<===[JENKINS REMOTING CAPACITY]===>".getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
