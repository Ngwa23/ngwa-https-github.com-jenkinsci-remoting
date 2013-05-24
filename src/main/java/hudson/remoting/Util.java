package hudson.remoting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Misc. I/O utilities
 *
 * @author Kohsuke Kawaguchi
 */
class Util {
    /**
     * Gets the file name portion from a qualified '/'-separate resource path name.
     *
     * Acts like basename(1)
     */
    static String getBaseName(String path) {
        return path.substring(path.lastIndexOf('/')+1);
    }

    static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(in,baos);
        return baos.toByteArray();
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[8192];
            int len;
            while((len=in.read(buf))>0)
                out.write(buf,0,len);
        } finally {
            in.close();
        }
    }

    static File makeResource(String name, byte[] image) throws IOException {
        File tmpFile = createTempDir();
        File resource = new File(tmpFile, name);
        resource.getParentFile().mkdirs();

        FileOutputStream fos = new FileOutputStream(resource);
        fos.write(image);
        fos.close();

        deleteDirectoryOnExit(tmpFile);

        return resource;
    }

    static File createTempDir() throws IOException {
    	// work around sun bug 6325169 on windows
    	// see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6325169
        int nRetry=0;
        while (true) {
            try {
                File tmpFile = File.createTempFile("jenkins-remoting", "");
                tmpFile.delete();
                tmpFile.mkdir();
                return tmpFile;
            } catch (IOException e) {
                if (nRetry++ < 100){
                    continue;
                }
                IOException nioe = new IOException("failed to create temp directory at default location, most probably at: "+System.getProperty("java.io.tmpdir"));
                nioe.initCause(e);
                throw nioe;
            }
        }
    }

    /** Instructs Java to recursively delete the given directory (dir) and its contents when the JVM exits.
     *  @param dir File  customer  representing directory to delete. If this file argument is not a directory, it will still
     *  be deleted. <p>
     *  The method works in Java 1.3, Java 1.4, Java 5.0 and Java 6.0; but it does not work with some early Java 6.0 versions
     *  See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6437591
     */
    static void deleteDirectoryOnExit(final File dir) {
        // Delete this on exit.  Delete on exit requests are processed in REVERSE order
        dir.deleteOnExit();

        // If it's a directory, visit its children.  This recursive walk has to be done AFTER calling deleteOnExit
        // on the directory itself because Java deletes the files to be deleted on exit in reverse order.
        if (dir.isDirectory()) {
            File[] childFiles = dir.listFiles();
            if (childFiles != null) { // listFiles may return null if there's an IO error
                for (File f: childFiles) { deleteDirectoryOnExit(f); }
            }
        }
    }
}
