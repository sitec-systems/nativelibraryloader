/*
 * Project: 14_0017_NativeLibraryLoader
 * $Header: $
 * Author:  Mattes Standfuss
 * Last Change:
 *    by:   $Author: $
 *    date: $Date:   $
 * Copyright (c): sitec systems GmbH, 2014
 */
package de.sitec.nativelibraryloader;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An custom class loader with caching of the classes.
 * @author sitec systems GmbH
 * @since 1.0
 */
/* package */ class NativeLibraryLoaderClassLoader extends ClassLoader
{
    private final Map<String, Class<?>> loadedClasses = new HashMap<>();

    @Override
    public Class<?> findClass(final String name) throws ClassNotFoundException 
    {
        if (loadedClasses.containsKey(name)) 
        {
            return loadedClasses.get(name);
        }

        try 
        {
            final byte[] classData = loadClassData(name);
            final Class<?> clazz = defineClass(name, classData, 0, classData.length);
            resolveClass(clazz);
            loadedClasses.put(name, clazz);

            return clazz;
        } 
        catch (final IOException ex) 
        {
            throw new ClassNotFoundException("Class [" + name+ "] could not be found", ex);
        }
    }

    /**
     * Loads the class file into <code>byte[]</code>.
     * @param name The name of the class e.g. de.sitec.nativelibraryloadert.LoadEngine}
     * @return The class file as <code>byte[]</code>
     * @throws IOException If the reading of the class file has failed
     * @since 1.0
     */
    private byte[] loadClassData(String name) throws IOException 
    {
        try(final BufferedInputStream in = new BufferedInputStream(
                ClassLoader.getSystemResourceAsStream(name.replace(".", "/")
                        + ".class"));
                final ByteArrayOutputStream bos = new ByteArrayOutputStream())
        {
            int i;
            
            while ((i = in.read()) != -1) 
            {
                bos.write(i);
            }
            
            return bos.toByteArray();
        }
    }
    
    @Override
    public String toString() 
    {
        return NativeLibraryLoaderClassLoader.class.getName();
    }
}
