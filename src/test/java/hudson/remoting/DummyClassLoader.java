/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.remoting;


import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URL;
import org.apache.commons.io.IOUtils;

/**
 * Used to load a dummy class
 * out of nowhere, to test {@link RemoteClassLoader} by creating a class
 * that only exists on one side of the channel but not the other.
 *
 * <p>
 * Given a class in a "remoting" package, this classloader is capable of loading the same version of the class
 * in the "rem0ting" package.
 *
 * @author Kohsuke Kawaguchi
 */
class DummyClassLoader extends ClassLoader {

    private final String physicalName;
    private final String logicalName;
    private final String physicalPath;
    private final String logicalPath;

    /** Uses {@link TestCallable}. */
    public DummyClassLoader(ClassLoader parent) {
        this(parent, TestCallable.class);
    }

    public DummyClassLoader(ClassLoader parent, Class<?> c) {
        super(parent);
        physicalName = c.getName();
        assert physicalName.contains("remoting.Test");
        logicalName = physicalName.replace("remoting", "rem0ting");
        physicalPath = physicalName.replace('.', '/') + ".class";
        logicalPath = logicalName.replace('.', '/') + ".class";
    }

    /**
     * Loads a class that looks like an exact clone of the named class under
     * a different class name.
     */
    public Object load() {
        try {
            return loadClass(logicalName).newInstance();
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (ClassNotFoundException e) {
            throw new Error(e);
        }
    }


    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if(name.equals(logicalName)) {
            // rename a class
            try {
                byte[] bytes = loadTransformedClassImage();
                return defineClass(name,bytes,0,bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException("Bytecode manipulation failed",e);
            }
        }

        return super.findClass(name);
    }

    private byte[] loadTransformedClassImage() throws IOException {
        InputStream in = getResourceAsStream(physicalPath);
        String data = IOUtils.toString(in, "ISO-8859-1");
        // Single-character substitutions will not change length fields in bytecode etc.
        String data2 = data.replaceAll("remoting(.)Test", "rem0ting$1Test");
        return data2.getBytes("ISO-8859-1");
    }


    protected URL findResource(String name) {
        if (name.equals(logicalPath)) {
            try {
                File f = File.createTempFile("rmiTest","class");
                OutputStream os = new FileOutputStream(f);
                os.write(loadTransformedClassImage());
                os.close();
                f.deleteOnExit();
                return f.toURI().toURL();
            } catch (IOException e) {
                return null;
            }
        }
        return super.findResource(name);
    }

    @Override public String toString() {
        return super.toString() + "[" + physicalName + "]";
    }

}
