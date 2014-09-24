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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * An util for loading native library basend on system architecture and operating system.
 * It extracts the library from the JAR file to temporary directory.
 * The support of multiple library based on namespace convention. The path by 
 * convetion has following stucture at the JAR. <p><code>/native/namespace/os/
 * architecture/</code></p>
 * @author sitec systems GmbH
 * @since 1.0
 */
public class NativeLibraryLoaderImpl implements NativeLibraryLoader
{
    private NativeLibraryLoaderClassLoader loaderClassLoader;
    private Class osDataClass;
    private Class loaderClass;
    private Object loaderObject;
    private Method loadMethod;
    
    private static final String OS_DATA_NAMESPACE = "de.sitec.nativelibraryloader.LoadEngine$OsData";
    private static final String LOADER_NAMESPACE = "de.sitec.nativelibraryloader.LoadEngine";
    private static final String LOAD_METHOD_NAME = "loadLibrary";

    /**
     * Constructor. Loads the custom class loader for clean resource handling.
     * Creates an temp directory for the extraction of native librarys.
     * @param namespace The namespace of the project (e.g. de.sitec.nativelibraryloader)
     * @throws IOException The loading of loader class or the creation of temp
     *         directory has failed
     * @since 1.0
     */
    public NativeLibraryLoaderImpl(final String namespace) throws IOException
    {
        try
        {
            loaderClassLoader = new NativeLibraryLoaderClassLoader();
            osDataClass = loaderClassLoader.findClass(OS_DATA_NAMESPACE);
            loaderClass = loaderClassLoader.findClass(LOADER_NAMESPACE);
            loaderObject = loaderClass.getDeclaredConstructor(String.class).newInstance(namespace);
            loadMethod = loaderClass.getMethod(LOAD_METHOD_NAME, String.class);
        }
        catch (final ClassNotFoundException | NoSuchMethodException 
                | InstantiationException | IllegalAccessException ex)
        {
            throw new IOException("The NativeLibraryLoader access has failed", ex);
        }
        catch (final InvocationTargetException ie)
        {
            throw new IOException("The NativeLibraryLoader has thrown a exception", ie.getCause());
        }
    }
    
    /* @inheritDoc */
    @Override
    public void loadLibrary(final String library) throws IOException
    {
        try
        {
            loadMethod.invoke(loaderObject, library);
        }
        catch (final IllegalAccessException | IllegalArgumentException ex)
        {
            throw new IOException("Load method loadLibrary has failed", ex);
        }
        catch (final InvocationTargetException ex)
        {
            throw new IOException("Method loadLibrary has thrown a exception", ex.getCause());
        }
    }
    
    /* @inheritDoc */
    @Override
    public void close()
    {
        if(loadMethod != null)
        {
            loadMethod = null;
        }
        
        if(loaderObject != null)
        {
            loaderObject = null;
        }
        
        if(loaderClass != null)
        {
            loaderClass = null;
        }
        
        if(osDataClass != null)
        {
            osDataClass = null;
        }
        
        if(loaderClassLoader != null)
        {
            loaderClassLoader = null;
        }
        
        System.gc();
    }
}
